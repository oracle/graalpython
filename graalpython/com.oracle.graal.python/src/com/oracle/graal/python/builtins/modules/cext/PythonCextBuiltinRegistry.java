/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CHAR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Double;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.INT8_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.LONG_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Long;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PYMODULEDEF_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_CAPSULE_DESTRUCTOR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_COMPILER_FLAGS;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_GIL_STATE_STATE;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_THREAD_TYPE_LOCK;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_UCS4;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyASCIIObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyAsyncMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyBufferProcs;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyByteArrayObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCFunctionObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCodeObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCompactUnicodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyDescrObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyGetSetDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyInstanceMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyListObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyLongObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMappingMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMemberDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodDescrObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyNumberMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectWrapper;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PySequenceMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PySetObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PySliceObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTupleObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyUnicodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyVarObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_hash_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.SIZE_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UINTPTR_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VA_LIST_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.WCHAR_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.allocfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.binaryfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.descrgetfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.descrsetfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.destructor;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.freefunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.func_voidvoid;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getattrfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getattrofunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getbufferproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getiterfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getter;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.hashfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.initproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.inquiry;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.iternextfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.lenfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.mmap_object;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.newfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.objobjargproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.objobjproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.releasebufferproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.reprfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.richcmpfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setattrfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setattrofunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setter;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ssizeargfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ssizeobjargproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ternaryfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.traverseproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.unaryfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.vectorcallfunc;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinExecutable;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;

public abstract class PythonCextBuiltinRegistry {

    private PythonCextBuiltinRegistry() {
        // no instances
    }

    /*
     * GENERATED CODE - DO NOT MODIFY
     */
    // {{start CAPI_BUILTINS}}
    // GENERATED CODE - see CApiCodeGen
    // This can be re-generated using the 'mx python-capi-forwards' command or
    // by executing the main class CApiCodeGen
    public static final CApiBuiltinExecutable[] builtins = {
                    new CApiBuiltinExecutable("PyByteArray_Resize", Direct, Int,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 0),
                    new CApiBuiltinExecutable("PyBytes_FromObject", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 1),
                    new CApiBuiltinExecutable("PyBytes_Size", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject}, 2),
                    new CApiBuiltinExecutable("PyCallIter_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 3),
                    new CApiBuiltinExecutable("PyCallable_Check", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 4),
                    new CApiBuiltinExecutable("PyCapsule_GetContext", Direct, Pointer,
                                    new ArgDescriptor[]{PyObject}, 5),
                    new CApiBuiltinExecutable("PyCapsule_GetDestructor", Direct, PY_CAPSULE_DESTRUCTOR,
                                    new ArgDescriptor[]{PyObject}, 6),
                    new CApiBuiltinExecutable("PyCapsule_GetName", Direct, ConstCharPtr,
                                    new ArgDescriptor[]{PyObject}, 7),
                    new CApiBuiltinExecutable("PyCapsule_GetPointer", Direct, Pointer,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 8),
                    new CApiBuiltinExecutable("PyCapsule_Import", Direct, Pointer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, Int}, 9),
                    new CApiBuiltinExecutable("PyCapsule_IsValid", Direct, Int,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 10),
                    new CApiBuiltinExecutable("PyCapsule_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{Pointer, ConstCharPtrAsTruffleString, PY_CAPSULE_DESTRUCTOR}, 11),
                    new CApiBuiltinExecutable("PyCapsule_SetContext", Direct, Int,
                                    new ArgDescriptor[]{PyObject, Pointer}, 12),
                    new CApiBuiltinExecutable("PyCapsule_SetDestructor", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PY_CAPSULE_DESTRUCTOR}, 13),
                    new CApiBuiltinExecutable("PyCapsule_SetName", Direct, Int,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 14),
                    new CApiBuiltinExecutable("PyCapsule_SetPointer", Direct, Int,
                                    new ArgDescriptor[]{PyObject, Pointer}, 15),
                    new CApiBuiltinExecutable("PyClassMethod_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 16),
                    new CApiBuiltinExecutable("PyCode_New", Direct, PyCodeObjectTransfer,
                                    new ArgDescriptor[]{Int, Int, Int, Int, Int, PyObject, PyObject, PyObject, PyObject, PyObject, PyObject, PyObject, PyObject, Int, PyObject}, 17),
                    new CApiBuiltinExecutable("PyCode_NewEmpty", Direct, PyCodeObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int}, 18),
                    new CApiBuiltinExecutable("PyCode_NewWithPosOnlyArgs", Direct, PyCodeObjectTransfer,
                                    new ArgDescriptor[]{Int, Int, Int, Int, Int, Int, PyObject, PyObject, PyObject, PyObject, PyObject, PyObject, PyObject, PyObject, Int, PyObject}, 19),
                    new CApiBuiltinExecutable("PyComplex_FromDoubles", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{Double, Double}, 20),
                    new CApiBuiltinExecutable("PyComplex_ImagAsDouble", Direct, Double,
                                    new ArgDescriptor[]{PyObject}, 21),
                    new CApiBuiltinExecutable("PyComplex_RealAsDouble", Direct, Double,
                                    new ArgDescriptor[]{PyObject}, 22),
                    new CApiBuiltinExecutable("PyContextVar_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, PyObject}, 23),
                    new CApiBuiltinExecutable("PyContextVar_Set", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 24),
                    new CApiBuiltinExecutable("PyDictProxy_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 25),
                    new CApiBuiltinExecutable("PyDict_Clear", Direct, Void,
                                    new ArgDescriptor[]{PyObject}, 26),
                    new CApiBuiltinExecutable("PyDict_Contains", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 27),
                    new CApiBuiltinExecutable("PyDict_Copy", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 28),
                    new CApiBuiltinExecutable("PyDict_DelItem", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 29),
                    new CApiBuiltinExecutable("PyDict_GetItem", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyObject, PyObject}, 30),
                    new CApiBuiltinExecutable("PyDict_GetItemWithError", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyObject, PyObject}, 31),
                    new CApiBuiltinExecutable("PyDict_Keys", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 32),
                    new CApiBuiltinExecutable("PyDict_Merge", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject, Int}, 33),
                    new CApiBuiltinExecutable("PyDict_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 34),
                    new CApiBuiltinExecutable("PyDict_SetDefault", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 35),
                    new CApiBuiltinExecutable("PyDict_SetItem", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 36),
                    new CApiBuiltinExecutable("PyDict_Size", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject}, 37),
                    new CApiBuiltinExecutable("PyDict_Update", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 38),
                    new CApiBuiltinExecutable("PyDict_Values", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 39),
                    new CApiBuiltinExecutable("PyErr_Display", Direct, Void,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 40),
                    new CApiBuiltinExecutable("PyErr_GivenExceptionMatches", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 41),
                    new CApiBuiltinExecutable("PyErr_NewException", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, PyObject, PyObject}, 42),
                    new CApiBuiltinExecutable("PyErr_NewExceptionWithDoc", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, PyObject, PyObject}, 43),
                    new CApiBuiltinExecutable("PyErr_Occurred", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{}, 44),
                    new CApiBuiltinExecutable("PyErr_PrintEx", Direct, Void,
                                    new ArgDescriptor[]{Int}, 45),
                    new CApiBuiltinExecutable("PyErr_Restore", Direct, Void,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 46),
                    new CApiBuiltinExecutable("PyErr_SetExcInfo", Direct, Void,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 47),
                    new CApiBuiltinExecutable("PyEval_GetBuiltins", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{}, 48),
                    new CApiBuiltinExecutable("PyEval_RestoreThread", Direct, Void,
                                    new ArgDescriptor[]{PyThreadState}, 49),
                    new CApiBuiltinExecutable("PyEval_SaveThread", Direct, PyThreadState,
                                    new ArgDescriptor[]{}, 50),
                    new CApiBuiltinExecutable("PyException_GetContext", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 51),
                    new CApiBuiltinExecutable("PyException_SetCause", Direct, Void,
                                    new ArgDescriptor[]{PyObject, PyObject}, 52),
                    new CApiBuiltinExecutable("PyException_SetContext", Direct, Void,
                                    new ArgDescriptor[]{PyObject, PyObject}, 53),
                    new CApiBuiltinExecutable("PyException_SetTraceback", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 54),
                    new CApiBuiltinExecutable("PyFile_WriteObject", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject, Int}, 55),
                    new CApiBuiltinExecutable("PyFloat_AsDouble", Direct, Double,
                                    new ArgDescriptor[]{PyObject}, 56),
                    new CApiBuiltinExecutable("PyFloat_FromDouble", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{Double}, 57),
                    new CApiBuiltinExecutable("PyFrame_New", Direct, PyFrameObjectTransfer,
                                    new ArgDescriptor[]{PyThreadState, PyCodeObject, PyObject, PyObject}, 58),
                    new CApiBuiltinExecutable("PyFrozenSet_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 59),
                    new CApiBuiltinExecutable("PyGILState_Ensure", Direct, PY_GIL_STATE_STATE,
                                    new ArgDescriptor[]{}, 60),
                    new CApiBuiltinExecutable("PyGILState_Release", Direct, Void,
                                    new ArgDescriptor[]{PY_GIL_STATE_STATE}, 61),
                    new CApiBuiltinExecutable("PyImport_GetModuleDict", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{}, 62),
                    new CApiBuiltinExecutable("PyImport_Import", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObjectAsTruffleString}, 63),
                    new CApiBuiltinExecutable("PyImport_ImportModule", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString}, 64),
                    new CApiBuiltinExecutable("PyImport_ImportModuleLevelObject", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObjectAsTruffleString, PyObject, PyObject, PyObject, Int}, 65),
                    new CApiBuiltinExecutable("PyImport_ImportModuleNoBlock", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString}, 66),
                    new CApiBuiltinExecutable("PyIndex_Check", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 67),
                    new CApiBuiltinExecutable("PyInstanceMethod_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 68),
                    new CApiBuiltinExecutable("PyIter_Next", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 69),
                    new CApiBuiltinExecutable("PyList_Append", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 70),
                    new CApiBuiltinExecutable("PyList_AsTuple", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 71),
                    new CApiBuiltinExecutable("PyList_GetItem", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 72),
                    new CApiBuiltinExecutable("PyList_GetSlice", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t, Py_ssize_t}, 73),
                    new CApiBuiltinExecutable("PyList_Insert", Direct, Int,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t, PyObject}, 74),
                    new CApiBuiltinExecutable("PyList_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{Py_ssize_t}, 75),
                    new CApiBuiltinExecutable("PyList_Reverse", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 76),
                    new CApiBuiltinExecutable("PyList_SetItem", Direct, Int,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t, PyObjectTransfer}, 77),
                    new CApiBuiltinExecutable("PyList_SetSlice", Direct, Int,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t, Py_ssize_t, PyObject}, 78),
                    new CApiBuiltinExecutable("PyList_Size", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject}, 79),
                    new CApiBuiltinExecutable("PyList_Sort", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 80),
                    new CApiBuiltinExecutable("PyLong_AsVoidPtr", Direct, Pointer,
                                    new ArgDescriptor[]{PyObject}, 81),
                    new CApiBuiltinExecutable("PyLong_FromDouble", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{Double}, 82),
                    new CApiBuiltinExecutable("PyLong_FromLong", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{Long}, 83),
                    new CApiBuiltinExecutable("PyLong_FromLongLong", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{LONG_LONG}, 84),
                    new CApiBuiltinExecutable("PyLong_FromSize_t", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{SIZE_T}, 85),
                    new CApiBuiltinExecutable("PyLong_FromSsize_t", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{Py_ssize_t}, 86),
                    new CApiBuiltinExecutable("PyLong_FromUnsignedLong", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{UNSIGNED_LONG}, 87),
                    new CApiBuiltinExecutable("PyLong_FromUnsignedLongLong", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{UNSIGNED_LONG_LONG}, 88),
                    new CApiBuiltinExecutable("PyMapping_Check", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 89),
                    new CApiBuiltinExecutable("PyMapping_Items", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 90),
                    new CApiBuiltinExecutable("PyMapping_Keys", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 91),
                    new CApiBuiltinExecutable("PyMapping_Size", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject}, 92),
                    new CApiBuiltinExecutable("PyMapping_Values", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 93),
                    new CApiBuiltinExecutable("PyMemoryView_FromObject", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 94),
                    new CApiBuiltinExecutable("PyMemoryView_GetContiguous", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Int, CHAR}, 95),
                    new CApiBuiltinExecutable("PyMethod_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 96),
                    new CApiBuiltinExecutable("PyModule_AddIntConstant", Direct, Int,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString, Long}, 97),
                    new CApiBuiltinExecutable("PyModule_AddObjectRef", Direct, Int,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString, PyObject}, 98),
                    new CApiBuiltinExecutable("PyModule_GetNameObject", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 99),
                    new CApiBuiltinExecutable("PyModule_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString}, 100),
                    new CApiBuiltinExecutable("PyModule_NewObject", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObjectAsTruffleString}, 101),
                    new CApiBuiltinExecutable("PyModule_SetDocString", Direct, Int,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 102),
                    new CApiBuiltinExecutable("PyNumber_Absolute", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 103),
                    new CApiBuiltinExecutable("PyNumber_Check", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 104),
                    new CApiBuiltinExecutable("PyNumber_Divmod", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 105),
                    new CApiBuiltinExecutable("PyNumber_Float", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 106),
                    new CApiBuiltinExecutable("PyNumber_InPlacePower", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 107),
                    new CApiBuiltinExecutable("PyNumber_Index", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 108),
                    new CApiBuiltinExecutable("PyNumber_Long", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 109),
                    new CApiBuiltinExecutable("PyNumber_Power", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 110),
                    new CApiBuiltinExecutable("PyNumber_ToBase", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Int}, 111),
                    new CApiBuiltinExecutable("PyOS_FSPath", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 112),
                    new CApiBuiltinExecutable("PyObject_ASCII", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 113),
                    new CApiBuiltinExecutable("PyObject_AsFileDescriptor", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 114),
                    new CApiBuiltinExecutable("PyObject_Bytes", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 115),
                    new CApiBuiltinExecutable("PyObject_ClearWeakRefs", Direct, Void,
                                    new ArgDescriptor[]{PyObject}, 116),
                    new CApiBuiltinExecutable("PyObject_DelItem", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 117),
                    new CApiBuiltinExecutable("PyObject_Dir", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 118),
                    new CApiBuiltinExecutable("PyObject_Format", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 119),
                    new CApiBuiltinExecutable("PyObject_GC_Track", Direct, Void,
                                    new ArgDescriptor[]{Pointer}, 120),
                    new CApiBuiltinExecutable("PyObject_GC_UnTrack", Direct, Void,
                                    new ArgDescriptor[]{Pointer}, 121),
                    new CApiBuiltinExecutable("PyObject_GetDoc", Direct, ConstCharPtr,
                                    new ArgDescriptor[]{PyObject}, 122),
                    new CApiBuiltinExecutable("PyObject_GetItem", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 123),
                    new CApiBuiltinExecutable("PyObject_GetIter", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 124),
                    new CApiBuiltinExecutable("PyObject_HasAttr", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 125),
                    new CApiBuiltinExecutable("PyObject_HasAttrString", Direct, Int,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 126),
                    new CApiBuiltinExecutable("PyObject_Hash", Direct, Py_hash_t,
                                    new ArgDescriptor[]{PyObject}, 127),
                    new CApiBuiltinExecutable("PyObject_HashNotImplemented", Direct, Py_hash_t,
                                    new ArgDescriptor[]{PyObject}, 128),
                    new CApiBuiltinExecutable("PyObject_IsInstance", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 129),
                    new CApiBuiltinExecutable("PyObject_IsSubclass", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 130),
                    new CApiBuiltinExecutable("PyObject_IsTrue", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 131),
                    new CApiBuiltinExecutable("PyObject_LengthHint", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 132),
                    new CApiBuiltinExecutable("PyObject_Repr", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 133),
                    new CApiBuiltinExecutable("PyObject_RichCompare", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, Int}, 134),
                    new CApiBuiltinExecutable("PyObject_SetDoc", Direct, Int,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 135),
                    new CApiBuiltinExecutable("PyObject_SetItem", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 136),
                    new CApiBuiltinExecutable("PyObject_Size", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject}, 137),
                    new CApiBuiltinExecutable("PyObject_Str", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 138),
                    new CApiBuiltinExecutable("PyObject_Type", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 139),
                    new CApiBuiltinExecutable("PyRun_StringFlags", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, Int, PyObject, PyObject, PY_COMPILER_FLAGS}, 140),
                    new CApiBuiltinExecutable("PySeqIter_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 141),
                    new CApiBuiltinExecutable("PySequence_Check", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 142),
                    new CApiBuiltinExecutable("PySequence_Concat", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 143),
                    new CApiBuiltinExecutable("PySequence_Contains", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 144),
                    new CApiBuiltinExecutable("PySequence_DelItem", Direct, Int,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 145),
                    new CApiBuiltinExecutable("PySequence_GetItem", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 146),
                    new CApiBuiltinExecutable("PySequence_GetSlice", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t, Py_ssize_t}, 147),
                    new CApiBuiltinExecutable("PySequence_InPlaceConcat", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 148),
                    new CApiBuiltinExecutable("PySequence_InPlaceRepeat", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 149),
                    new CApiBuiltinExecutable("PySequence_Length", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject}, 150),
                    new CApiBuiltinExecutable("PySequence_List", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 151),
                    new CApiBuiltinExecutable("PySequence_Repeat", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 152),
                    new CApiBuiltinExecutable("PySequence_SetItem", Direct, Int,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t, PyObject}, 153),
                    new CApiBuiltinExecutable("PySequence_Size", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject}, 154),
                    new CApiBuiltinExecutable("PySequence_Tuple", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 155),
                    new CApiBuiltinExecutable("PySet_Add", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 156),
                    new CApiBuiltinExecutable("PySet_Clear", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 157),
                    new CApiBuiltinExecutable("PySet_Contains", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 158),
                    new CApiBuiltinExecutable("PySet_Discard", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 159),
                    new CApiBuiltinExecutable("PySet_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 160),
                    new CApiBuiltinExecutable("PySet_Pop", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 161),
                    new CApiBuiltinExecutable("PySet_Size", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject}, 162),
                    new CApiBuiltinExecutable("PySlice_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 163),
                    new CApiBuiltinExecutable("PyStaticMethod_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 164),
                    new CApiBuiltinExecutable("PyStructSequence_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyTypeObject}, 165),
                    new CApiBuiltinExecutable("PySys_GetObject", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString}, 166),
                    new CApiBuiltinExecutable("PyThreadState_Get", Direct, PyThreadState,
                                    new ArgDescriptor[]{}, 167),
                    new CApiBuiltinExecutable("PyThreadState_GetDict", Direct, PyObject,
                                    new ArgDescriptor[]{}, 168),
                    new CApiBuiltinExecutable("PyThread_acquire_lock", Direct, Int,
                                    new ArgDescriptor[]{PY_THREAD_TYPE_LOCK, Int}, 169),
                    new CApiBuiltinExecutable("PyThread_allocate_lock", Direct, PY_THREAD_TYPE_LOCK,
                                    new ArgDescriptor[]{}, 170),
                    new CApiBuiltinExecutable("PyThread_release_lock", Direct, Void,
                                    new ArgDescriptor[]{PY_THREAD_TYPE_LOCK}, 171),
                    new CApiBuiltinExecutable("PyTraceBack_Here", Direct, Int,
                                    new ArgDescriptor[]{PyFrameObject}, 172),
                    new CApiBuiltinExecutable("PyTraceMalloc_Track", Direct, Int,
                                    new ArgDescriptor[]{UNSIGNED_INT, UINTPTR_T, SIZE_T}, 173),
                    new CApiBuiltinExecutable("PyTraceMalloc_Untrack", Direct, Int,
                                    new ArgDescriptor[]{UNSIGNED_INT, UINTPTR_T}, 174),
                    new CApiBuiltinExecutable("PyTruffleByteArray_FromStringAndSize", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{INT8_T_PTR, Py_ssize_t}, 175),
                    new CApiBuiltinExecutable("PyTruffleBytes_Concat", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 176),
                    new CApiBuiltinExecutable("PyTruffleBytes_FromFormat", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, PyObject}, 177),
                    new CApiBuiltinExecutable("PyTruffleBytes_FromStringAndSize", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtr, Py_ssize_t}, 178),
                    new CApiBuiltinExecutable("PyTruffleCMethod_NewEx", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyMethodDef, ConstCharPtrAsTruffleString, Pointer, Int, Int, PyObject, PyObject, PyTypeObject, ConstCharPtrAsTruffleString}, 179),
                    new CApiBuiltinExecutable("PyTruffleComplex_AsCComplex", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 180),
                    new CApiBuiltinExecutable("PyTruffleContextVar_Get", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, Pointer}, 181),
                    new CApiBuiltinExecutable("PyTruffleDescr_NewClassMethod", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{Pointer, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int, Int, Pointer, PyTypeObject}, 182),
                    new CApiBuiltinExecutable("PyTruffleDescr_NewGetSet", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, PyTypeObject, Pointer, Pointer, ConstCharPtrAsTruffleString, Pointer}, 183),
                    new CApiBuiltinExecutable("PyTruffleDict_Next", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 184),
                    new CApiBuiltinExecutable("PyTruffleErr_Fetch", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 185),
                    new CApiBuiltinExecutable("PyTruffleErr_GetExcInfo", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 186),
                    new CApiBuiltinExecutable("PyTruffleHash_InitSecret", Ignored, Void,
                                    new ArgDescriptor[]{Pointer}, 187),
                    new CApiBuiltinExecutable("PyTruffleLong_AsPrimitive", Ignored, Long,
                                    new ArgDescriptor[]{PyObject, Int, Long}, 188),
                    new CApiBuiltinExecutable("PyTruffleLong_FromString", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, Int, Int}, 189),
                    new CApiBuiltinExecutable("PyTruffleLong_One", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 190),
                    new CApiBuiltinExecutable("PyTruffleLong_Zero", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 191),
                    new CApiBuiltinExecutable("PyTruffleModule_AddFunctionToModule", Ignored, Int,
                                    new ArgDescriptor[]{Pointer, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, Pointer}, 192),
                    new CApiBuiltinExecutable("PyTruffleNumber_BinOp", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, Int}, 193),
                    new CApiBuiltinExecutable("PyTruffleNumber_InPlaceBinOp", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, Int}, 194),
                    new CApiBuiltinExecutable("PyTruffleNumber_UnaryOp", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Int}, 195),
                    new CApiBuiltinExecutable("PyTruffleObject_CallFunctionObjArgs", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, VA_LIST_PTR}, 196),
                    new CApiBuiltinExecutable("PyTruffleObject_CallMethodObjArgs", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, VA_LIST_PTR}, 197),
                    new CApiBuiltinExecutable("PyTruffleObject_GenericGetAttr", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 198),
                    new CApiBuiltinExecutable("PyTruffleObject_GenericSetAttr", Ignored, Int,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 199),
                    new CApiBuiltinExecutable("PyTruffleObject_GetItemString", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 200),
                    new CApiBuiltinExecutable("PyTruffleState_FindModule", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PYMODULEDEF_PTR}, 201),
                    new CApiBuiltinExecutable("PyTruffleStructSequence_InitType2", Ignored, Int,
                                    new ArgDescriptor[]{PyTypeObject, Pointer, Pointer, Int}, 202),
                    new CApiBuiltinExecutable("PyTruffleStructSequence_NewType", Ignored, PyTypeObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Pointer, Pointer, Int}, 203),
                    new CApiBuiltinExecutable("PyTruffleToCharPointer", Ignored, Pointer,
                                    new ArgDescriptor[]{PyObject}, 204),
                    new CApiBuiltinExecutable("PyTruffleType_AddFunctionToType", Ignored, Int,
                                    new ArgDescriptor[]{Pointer, PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, Pointer}, 205),
                    new CApiBuiltinExecutable("PyTruffleType_AddGetSet", Ignored, Int,
                                    new ArgDescriptor[]{PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Pointer, Pointer, Pointer}, 206),
                    new CApiBuiltinExecutable("PyTruffleType_AddMember", Ignored, Int,
                                    new ArgDescriptor[]{PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Int, Py_ssize_t, Int, Pointer}, 207),
                    new CApiBuiltinExecutable("PyTruffleType_AddSlot", Ignored, Int,
                                    new ArgDescriptor[]{PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, Pointer}, 208),
                    new CApiBuiltinExecutable("PyTruffleUnicode_Decode", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, 209),
                    new CApiBuiltinExecutable("PyTruffleUnicode_DecodeUTF8Stateful", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{Pointer, ConstCharPtrAsTruffleString, Int}, 210),
                    new CApiBuiltinExecutable("PyTruffleUnicode_InternInPlace", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyObject}, 211),
                    new CApiBuiltinExecutable("PyTruffleUnicode_New", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{Pointer, Py_ssize_t, PY_UCS4}, 212),
                    new CApiBuiltinExecutable("PyTruffle_Arg_ParseTupleAndKeywords", Ignored, Int,
                                    new ArgDescriptor[]{PyObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Pointer}, 213),
                    new CApiBuiltinExecutable("PyTruffle_ByteArray_EmptyWithCapacity", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{Py_ssize_t}, 214),
                    new CApiBuiltinExecutable("PyTruffle_Bytes_AsString", Ignored, Pointer,
                                    new ArgDescriptor[]{PyObject}, 215),
                    new CApiBuiltinExecutable("PyTruffle_Bytes_CheckEmbeddedNull", Ignored, Int,
                                    new ArgDescriptor[]{PyObject}, 216),
                    new CApiBuiltinExecutable("PyTruffle_Bytes_EmptyWithCapacity", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{Long}, 217),
                    new CApiBuiltinExecutable("PyTruffle_Compute_Mro", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyTypeObject, ConstCharPtrAsTruffleString}, 218),
                    new CApiBuiltinExecutable("PyTruffle_Debug", Direct, Int,
                                    new ArgDescriptor[]{Pointer}, 219),
                    new CApiBuiltinExecutable("PyTruffle_DebugTrace", Direct, Void,
                                    new ArgDescriptor[]{}, 220),
                    new CApiBuiltinExecutable("PyTruffle_Ellipsis", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 221),
                    new CApiBuiltinExecutable("PyTruffle_False", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 222),
                    new CApiBuiltinExecutable("PyTruffle_FatalErrorFunc", Ignored, Void,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int}, 223),
                    new CApiBuiltinExecutable("PyTruffle_FileSystemDefaultEncoding", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 224),
                    new CApiBuiltinExecutable("PyTruffle_Get_Inherited_Native_Slots", Ignored, Pointer,
                                    new ArgDescriptor[]{PyTypeObject, ConstCharPtrAsTruffleString}, 225),
                    new CApiBuiltinExecutable("PyTruffle_HashConstant", Ignored, Long,
                                    new ArgDescriptor[]{Int}, 226),
                    new CApiBuiltinExecutable("PyTruffle_LogString", Ignored, Void,
                                    new ArgDescriptor[]{Int, ConstCharPtrAsTruffleString}, 227),
                    new CApiBuiltinExecutable("PyTruffle_MemoryViewFromBuffer", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{Pointer, PyObject, Py_ssize_t, Int, Py_ssize_t, ConstCharPtrAsTruffleString, Int, Pointer, Pointer, Pointer, Pointer}, 228),
                    new CApiBuiltinExecutable("PyTruffle_Native_Options", Ignored, Int,
                                    new ArgDescriptor[]{}, 229),
                    new CApiBuiltinExecutable("PyTruffle_NewTypeDict", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyTypeObject}, 230),
                    new CApiBuiltinExecutable("PyTruffle_NoValue", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 231),
                    new CApiBuiltinExecutable("PyTruffle_None", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 232),
                    new CApiBuiltinExecutable("PyTruffle_NotImplemented", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 233),
                    new CApiBuiltinExecutable("PyTruffle_OS_DoubleToString", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{Double, Int, Int, Int}, 234),
                    new CApiBuiltinExecutable("PyTruffle_OS_StringToDouble", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, Int}, 235),
                    new CApiBuiltinExecutable("PyTruffle_Object_Alloc", Ignored, Int,
                                    new ArgDescriptor[]{Pointer, Long}, 236),
                    new CApiBuiltinExecutable("PyTruffle_Object_Free", Ignored, Int,
                                    new ArgDescriptor[]{Pointer}, 237),
                    new CApiBuiltinExecutable("PyTruffle_Register_NULL", Ignored, Void,
                                    new ArgDescriptor[]{Pointer}, 238),
                    new CApiBuiltinExecutable("PyTruffle_Set_Native_Slots", Ignored, Int,
                                    new ArgDescriptor[]{PyTypeObject, Pointer, Pointer}, 239),
                    new CApiBuiltinExecutable("PyTruffle_Set_SulongType", Ignored, Pointer,
                                    new ArgDescriptor[]{PyTypeObject, Pointer}, 240),
                    new CApiBuiltinExecutable("PyTruffle_ToNative", Direct, Int,
                                    new ArgDescriptor[]{Pointer}, 241),
                    new CApiBuiltinExecutable("PyTruffle_Trace_Type", Ignored, Int,
                                    new ArgDescriptor[]{Pointer, Pointer}, 242),
                    new CApiBuiltinExecutable("PyTruffle_True", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{}, 243),
                    new CApiBuiltinExecutable("PyTruffle_Type", Ignored, PyTypeObject,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString}, 244),
                    new CApiBuiltinExecutable("PyTruffle_Type_Modified", Ignored, Int,
                                    new ArgDescriptor[]{PyTypeObject, ConstCharPtrAsTruffleString, PyObject}, 245),
                    new CApiBuiltinExecutable("PyTruffle_Unicode_AsUnicodeAndSize", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 246),
                    new CApiBuiltinExecutable("PyTruffle_Unicode_AsWideChar", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Int}, 247),
                    new CApiBuiltinExecutable("PyTruffle_Unicode_DecodeUTF32", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{Pointer, Py_ssize_t, ConstCharPtrAsTruffleString, Int}, 248),
                    new CApiBuiltinExecutable("PyTruffle_Unicode_FromFormat", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, VA_LIST_PTR}, 249),
                    new CApiBuiltinExecutable("PyTruffle_Unicode_FromWchar", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{Pointer, SIZE_T}, 250),
                    new CApiBuiltinExecutable("PyTruffle_tss_create", Ignored, Long,
                                    new ArgDescriptor[]{}, 251),
                    new CApiBuiltinExecutable("PyTruffle_tss_delete", Ignored, Void,
                                    new ArgDescriptor[]{Long}, 252),
                    new CApiBuiltinExecutable("PyTruffle_tss_get", Ignored, Pointer,
                                    new ArgDescriptor[]{Long}, 253),
                    new CApiBuiltinExecutable("PyTruffle_tss_set", Ignored, Int,
                                    new ArgDescriptor[]{Long, Pointer}, 254),
                    new CApiBuiltinExecutable("PyTuple_GetItem", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 255),
                    new CApiBuiltinExecutable("PyTuple_GetSlice", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t, Py_ssize_t}, 256),
                    new CApiBuiltinExecutable("PyTuple_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{Py_ssize_t}, 257),
                    new CApiBuiltinExecutable("PyTuple_SetItem", Direct, Int,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t, PyObjectTransfer}, 258),
                    new CApiBuiltinExecutable("PyTuple_Size", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject}, 259),
                    new CApiBuiltinExecutable("PyType_IsSubtype", Direct, Int,
                                    new ArgDescriptor[]{PyTypeObject, PyTypeObject}, 260),
                    new CApiBuiltinExecutable("PyUnicode_AsEncodedString", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, 261),
                    new CApiBuiltinExecutable("PyUnicode_AsUnicodeEscapeString", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 262),
                    new CApiBuiltinExecutable("PyUnicode_Compare", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 263),
                    new CApiBuiltinExecutable("PyUnicode_Concat", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 264),
                    new CApiBuiltinExecutable("PyUnicode_Contains", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject}, 265),
                    new CApiBuiltinExecutable("PyUnicode_DecodeFSDefault", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString}, 266),
                    new CApiBuiltinExecutable("PyUnicode_EncodeFSDefault", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 267),
                    new CApiBuiltinExecutable("PyUnicode_FindChar", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject, PY_UCS4, Py_ssize_t, Py_ssize_t, Int}, 268),
                    new CApiBuiltinExecutable("PyUnicode_Format", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 269),
                    new CApiBuiltinExecutable("PyUnicode_FromEncodedObject", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString}, 270),
                    new CApiBuiltinExecutable("PyUnicode_FromObject", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 271),
                    new CApiBuiltinExecutable("PyUnicode_FromOrdinal", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{Int}, 272),
                    new CApiBuiltinExecutable("PyUnicode_FromString", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString}, 273),
                    new CApiBuiltinExecutable("PyUnicode_Join", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 274),
                    new CApiBuiltinExecutable("PyUnicode_ReadChar", Direct, PY_UCS4,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 275),
                    new CApiBuiltinExecutable("PyUnicode_Replace", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject, Py_ssize_t}, 276),
                    new CApiBuiltinExecutable("PyUnicode_Split", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, Py_ssize_t}, 277),
                    new CApiBuiltinExecutable("PyUnicode_Substring", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t, Py_ssize_t}, 278),
                    new CApiBuiltinExecutable("PyUnicode_Tailmatch", Direct, Py_ssize_t,
                                    new ArgDescriptor[]{PyObject, PyObject, Py_ssize_t, Py_ssize_t, Int}, 279),
                    new CApiBuiltinExecutable("PyWeakref_GetObject", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyObject}, 280),
                    new CApiBuiltinExecutable("PyWeakref_NewRef", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 281),
                    new CApiBuiltinExecutable("Py_AtExit", Direct, Int,
                                    new ArgDescriptor[]{func_voidvoid}, 282),
                    new CApiBuiltinExecutable("Py_GenericAlias", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 283),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_length", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyASCIIObject}, 284),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_ascii", Ignored, UNSIGNED_INT,
                                    new ArgDescriptor[]{PyASCIIObject}, 285),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_compact", Ignored, UNSIGNED_INT,
                                    new ArgDescriptor[]{PyASCIIObject}, 286),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_kind", Ignored, UNSIGNED_INT,
                                    new ArgDescriptor[]{PyASCIIObject}, 287),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_ready", Ignored, UNSIGNED_INT,
                                    new ArgDescriptor[]{PyASCIIObject}, 288),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_wstr", Ignored, WCHAR_T_PTR,
                                    new ArgDescriptor[]{PyASCIIObject}, 289),
                    new CApiBuiltinExecutable("Py_get_PyAsyncMethods_am_aiter", Ignored, unaryfunc,
                                    new ArgDescriptor[]{PyAsyncMethods}, 290),
                    new CApiBuiltinExecutable("Py_get_PyAsyncMethods_am_anext", Ignored, unaryfunc,
                                    new ArgDescriptor[]{PyAsyncMethods}, 291),
                    new CApiBuiltinExecutable("Py_get_PyAsyncMethods_am_await", Ignored, unaryfunc,
                                    new ArgDescriptor[]{PyAsyncMethods}, 292),
                    new CApiBuiltinExecutable("Py_get_PyBufferProcs_bf_getbuffer", Ignored, getbufferproc,
                                    new ArgDescriptor[]{PyBufferProcs}, 293),
                    new CApiBuiltinExecutable("Py_get_PyBufferProcs_bf_releasebuffer", Ignored, releasebufferproc,
                                    new ArgDescriptor[]{PyBufferProcs}, 294),
                    new CApiBuiltinExecutable("Py_get_PyByteArrayObject_ob_exports", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyByteArrayObject}, 295),
                    new CApiBuiltinExecutable("Py_get_PyByteArrayObject_ob_start", Ignored, Pointer,
                                    new ArgDescriptor[]{PyByteArrayObject}, 296),
                    new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_ml", Ignored, PyMethodDef,
                                    new ArgDescriptor[]{PyCFunctionObject}, 297),
                    new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_module", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyCFunctionObject}, 298),
                    new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_self", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyCFunctionObject}, 299),
                    new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_weakreflist", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyCFunctionObject}, 300),
                    new CApiBuiltinExecutable("Py_get_PyCFunctionObject_vectorcall", Ignored, vectorcallfunc,
                                    new ArgDescriptor[]{PyCFunctionObject}, 301),
                    new CApiBuiltinExecutable("Py_get_PyCMethodObject_mm_class", Ignored, PyTypeObject,
                                    new ArgDescriptor[]{PyCMethodObject}, 302),
                    new CApiBuiltinExecutable("Py_get_PyCompactUnicodeObject_wstr_length", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyCompactUnicodeObject}, 303),
                    new CApiBuiltinExecutable("Py_get_PyDescrObject_d_name", Ignored, PyObject,
                                    new ArgDescriptor[]{PyDescrObject}, 304),
                    new CApiBuiltinExecutable("Py_get_PyDescrObject_d_type", Ignored, PyTypeObject,
                                    new ArgDescriptor[]{PyDescrObject}, 305),
                    new CApiBuiltinExecutable("Py_get_PyFrameObject_f_lineno", Ignored, Int,
                                    new ArgDescriptor[]{PyFrameObject}, 306),
                    new CApiBuiltinExecutable("Py_get_PyGetSetDef_closure", Ignored, Pointer,
                                    new ArgDescriptor[]{PyGetSetDef}, 307),
                    new CApiBuiltinExecutable("Py_get_PyGetSetDef_doc", Ignored, ConstCharPtrAsTruffleString,
                                    new ArgDescriptor[]{PyGetSetDef}, 308),
                    new CApiBuiltinExecutable("Py_get_PyGetSetDef_get", Ignored, getter,
                                    new ArgDescriptor[]{PyGetSetDef}, 309),
                    new CApiBuiltinExecutable("Py_get_PyGetSetDef_name", Ignored, ConstCharPtrAsTruffleString,
                                    new ArgDescriptor[]{PyGetSetDef}, 310),
                    new CApiBuiltinExecutable("Py_get_PyGetSetDef_set", Ignored, setter,
                                    new ArgDescriptor[]{PyGetSetDef}, 311),
                    new CApiBuiltinExecutable("Py_get_PyInstanceMethodObject_func", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyInstanceMethodObject}, 312),
                    new CApiBuiltinExecutable("Py_get_PyListObject_ob_item", Ignored, PyObjectPtr,
                                    new ArgDescriptor[]{PyListObject}, 313),
                    new CApiBuiltinExecutable("Py_get_PyLongObject_ob_digit", Ignored, Pointer,
                                    new ArgDescriptor[]{PyLongObject}, 314),
                    new CApiBuiltinExecutable("Py_get_PyMappingMethods_mp_ass_subscript", Ignored, objobjargproc,
                                    new ArgDescriptor[]{PyMappingMethods}, 315),
                    new CApiBuiltinExecutable("Py_get_PyMappingMethods_mp_length", Ignored, lenfunc,
                                    new ArgDescriptor[]{PyMappingMethods}, 316),
                    new CApiBuiltinExecutable("Py_get_PyMappingMethods_mp_subscript", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyMappingMethods}, 317),
                    new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_doc", Ignored, Pointer,
                                    new ArgDescriptor[]{PyMethodDef}, 318),
                    new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_flags", Ignored, Int,
                                    new ArgDescriptor[]{PyMethodDef}, 319),
                    new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_meth", Ignored, Pointer,
                                    new ArgDescriptor[]{PyMethodDef}, 320),
                    new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_name", Ignored, Pointer,
                                    new ArgDescriptor[]{PyMethodDef}, 321),
                    new CApiBuiltinExecutable("Py_get_PyMethodDescrObject_d_method", Ignored, PyMethodDef,
                                    new ArgDescriptor[]{PyMethodDescrObject}, 322),
                    new CApiBuiltinExecutable("Py_get_PyMethodObject_im_func", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyMethodObject}, 323),
                    new CApiBuiltinExecutable("Py_get_PyMethodObject_im_self", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyMethodObject}, 324),
                    new CApiBuiltinExecutable("Py_get_PyModuleDef_m_doc", Ignored, ConstCharPtrAsTruffleString,
                                    new ArgDescriptor[]{PyModuleDef}, 325),
                    new CApiBuiltinExecutable("Py_get_PyModuleDef_m_methods", Ignored, PyMethodDef,
                                    new ArgDescriptor[]{PyModuleDef}, 326),
                    new CApiBuiltinExecutable("Py_get_PyModuleDef_m_name", Ignored, ConstCharPtrAsTruffleString,
                                    new ArgDescriptor[]{PyModuleDef}, 327),
                    new CApiBuiltinExecutable("Py_get_PyModuleDef_m_size", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyModuleDef}, 328),
                    new CApiBuiltinExecutable("Py_get_PyModuleObject_md_def", Ignored, PyModuleDef,
                                    new ArgDescriptor[]{PyModuleObject}, 329),
                    new CApiBuiltinExecutable("Py_get_PyModuleObject_md_dict", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyModuleObject}, 330),
                    new CApiBuiltinExecutable("Py_get_PyModuleObject_md_state", Ignored, Pointer,
                                    new ArgDescriptor[]{PyModuleObject}, 331),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_absolute", Ignored, unaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 332),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_add", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 333),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_and", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 334),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_bool", Ignored, inquiry,
                                    new ArgDescriptor[]{PyNumberMethods}, 335),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_divmod", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 336),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_float", Ignored, unaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 337),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_floor_divide", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 338),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_index", Ignored, unaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 339),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_add", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 340),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_and", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 341),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_floor_divide", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 342),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_lshift", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 343),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_matrix_multiply", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 344),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_multiply", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 345),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_or", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 346),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_power", Ignored, ternaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 347),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_remainder", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 348),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_rshift", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 349),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_subtract", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 350),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_true_divide", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 351),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_xor", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 352),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_int", Ignored, unaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 353),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_invert", Ignored, unaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 354),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_lshift", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 355),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_matrix_multiply", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 356),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_multiply", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 357),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_negative", Ignored, unaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 358),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_or", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 359),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_positive", Ignored, unaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 360),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_power", Ignored, ternaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 361),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_remainder", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 362),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_rshift", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 363),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_subtract", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 364),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_true_divide", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 365),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_xor", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PyNumberMethods}, 366),
                    new CApiBuiltinExecutable("Py_get_PyObject_ob_refcnt", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyObjectWrapper}, 367),
                    new CApiBuiltinExecutable("Py_get_PyObject_ob_type", Ignored, PyTypeObject,
                                    new ArgDescriptor[]{PyObject}, 368),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_ass_item", Ignored, ssizeobjargproc,
                                    new ArgDescriptor[]{PySequenceMethods}, 369),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_concat", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PySequenceMethods}, 370),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_contains", Ignored, objobjproc,
                                    new ArgDescriptor[]{PySequenceMethods}, 371),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_inplace_concat", Ignored, binaryfunc,
                                    new ArgDescriptor[]{PySequenceMethods}, 372),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_inplace_repeat", Ignored, ssizeargfunc,
                                    new ArgDescriptor[]{PySequenceMethods}, 373),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_item", Ignored, ssizeargfunc,
                                    new ArgDescriptor[]{PySequenceMethods}, 374),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_length", Ignored, lenfunc,
                                    new ArgDescriptor[]{PySequenceMethods}, 375),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_repeat", Ignored, ssizeargfunc,
                                    new ArgDescriptor[]{PySequenceMethods}, 376),
                    new CApiBuiltinExecutable("Py_get_PySetObject_used", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PySetObject}, 377),
                    new CApiBuiltinExecutable("Py_get_PySliceObject_start", Ignored, PyObject,
                                    new ArgDescriptor[]{PySliceObject}, 378),
                    new CApiBuiltinExecutable("Py_get_PySliceObject_step", Ignored, PyObject,
                                    new ArgDescriptor[]{PySliceObject}, 379),
                    new CApiBuiltinExecutable("Py_get_PySliceObject_stop", Ignored, PyObject,
                                    new ArgDescriptor[]{PySliceObject}, 380),
                    new CApiBuiltinExecutable("Py_get_PyThreadState_dict", Ignored, PyObject,
                                    new ArgDescriptor[]{PyThreadState}, 381),
                    new CApiBuiltinExecutable("Py_get_PyTupleObject_ob_item", Ignored, PyObjectPtr,
                                    new ArgDescriptor[]{PyTupleObject}, 382),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_alloc", Ignored, allocfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 383),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_async", Ignored, PyAsyncMethods,
                                    new ArgDescriptor[]{PyTypeObject}, 384),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_buffer", Ignored, PyBufferProcs,
                                    new ArgDescriptor[]{PyTypeObject}, 385),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_mapping", Ignored, PyMappingMethods,
                                    new ArgDescriptor[]{PyTypeObject}, 386),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_number", Ignored, PyNumberMethods,
                                    new ArgDescriptor[]{PyTypeObject}, 387),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_sequence", Ignored, PySequenceMethods,
                                    new ArgDescriptor[]{PyTypeObject}, 388),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_base", Ignored, PyTypeObject,
                                    new ArgDescriptor[]{PyTypeObject}, 389),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_bases", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyTypeObject}, 390),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_basicsize", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyTypeObject}, 391),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_cache", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyTypeObject}, 392),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_call", Ignored, ternaryfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 393),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_clear", Ignored, inquiry,
                                    new ArgDescriptor[]{PyTypeObject}, 394),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_dealloc", Ignored, destructor,
                                    new ArgDescriptor[]{PyTypeObject}, 395),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_del", Ignored, destructor,
                                    new ArgDescriptor[]{PyTypeObject}, 396),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_descr_get", Ignored, descrgetfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 397),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_descr_set", Ignored, descrsetfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 398),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_dict", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyTypeObject}, 399),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_dictoffset", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyTypeObject}, 400),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_doc", Ignored, ConstCharPtrAsTruffleString,
                                    new ArgDescriptor[]{PyTypeObject}, 401),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_finalize", Ignored, destructor,
                                    new ArgDescriptor[]{PyTypeObject}, 402),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_flags", Ignored, UNSIGNED_LONG,
                                    new ArgDescriptor[]{PyTypeObject}, 403),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_free", Ignored, freefunc,
                                    new ArgDescriptor[]{PyTypeObject}, 404),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_getattr", Ignored, getattrfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 405),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_getattro", Ignored, getattrofunc,
                                    new ArgDescriptor[]{PyTypeObject}, 406),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_getset", Ignored, PyGetSetDef,
                                    new ArgDescriptor[]{PyTypeObject}, 407),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_hash", Ignored, hashfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 408),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_init", Ignored, initproc,
                                    new ArgDescriptor[]{PyTypeObject}, 409),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_is_gc", Ignored, inquiry,
                                    new ArgDescriptor[]{PyTypeObject}, 410),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_itemsize", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyTypeObject}, 411),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_iter", Ignored, getiterfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 412),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_iternext", Ignored, iternextfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 413),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_members", Ignored, PyMemberDef,
                                    new ArgDescriptor[]{PyTypeObject}, 414),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_methods", Ignored, PyMethodDef,
                                    new ArgDescriptor[]{PyTypeObject}, 415),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_mro", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyTypeObject}, 416),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_name", Ignored, ConstCharPtrAsTruffleString,
                                    new ArgDescriptor[]{PyTypeObject}, 417),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_new", Ignored, newfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 418),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_repr", Ignored, reprfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 419),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_richcompare", Ignored, richcmpfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 420),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_setattr", Ignored, setattrfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 421),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_setattro", Ignored, setattrofunc,
                                    new ArgDescriptor[]{PyTypeObject}, 422),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_str", Ignored, reprfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 423),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_subclasses", Ignored, PyObject,
                                    new ArgDescriptor[]{PyTypeObject}, 424),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_traverse", Ignored, traverseproc,
                                    new ArgDescriptor[]{PyTypeObject}, 425),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_vectorcall", Ignored, vectorcallfunc,
                                    new ArgDescriptor[]{PyTypeObject}, 426),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_vectorcall_offset", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyTypeObject}, 427),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_version_tag", Ignored, UNSIGNED_INT,
                                    new ArgDescriptor[]{PyTypeObject}, 428),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_weaklist", Ignored, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyTypeObject}, 429),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_weaklistoffset", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyTypeObject}, 430),
                    new CApiBuiltinExecutable("Py_get_PyUnicodeObject_data", Ignored, Pointer,
                                    new ArgDescriptor[]{PyUnicodeObject}, 431),
                    new CApiBuiltinExecutable("Py_get_PyVarObject_ob_size", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{PyVarObject}, 432),
                    new CApiBuiltinExecutable("Py_get_dummy", Ignored, Pointer,
                                    new ArgDescriptor[]{Pointer}, 433),
                    new CApiBuiltinExecutable("Py_get_mmap_object_data", Ignored, CHAR_PTR,
                                    new ArgDescriptor[]{mmap_object}, 434),
                    new CApiBuiltinExecutable("Py_set_PyByteArrayObject_ob_exports", Ignored, Void,
                                    new ArgDescriptor[]{PyByteArrayObject, Int}, 435),
                    new CApiBuiltinExecutable("Py_set_PyFrameObject_f_lineno", Ignored, Void,
                                    new ArgDescriptor[]{PyFrameObject, Int}, 436),
                    new CApiBuiltinExecutable("Py_set_PyModuleObject_md_def", Ignored, Void,
                                    new ArgDescriptor[]{PyModuleObject, PyModuleDef}, 437),
                    new CApiBuiltinExecutable("Py_set_PyModuleObject_md_state", Ignored, Void,
                                    new ArgDescriptor[]{PyModuleObject, Pointer}, 438),
                    new CApiBuiltinExecutable("Py_set_PyObject_ob_refcnt", Ignored, Void,
                                    new ArgDescriptor[]{PyObjectWrapper, Py_ssize_t}, 439),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_alloc", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, allocfunc}, 440),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_as_buffer", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, PyBufferProcs}, 441),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_base", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, PyTypeObject}, 442),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_bases", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, PyObject}, 443),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_basicsize", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, Py_ssize_t}, 444),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_clear", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, inquiry}, 445),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_dealloc", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, destructor}, 446),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_dict", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, PyObject}, 447),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_dictoffset", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, Py_ssize_t}, 448),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_finalize", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, destructor}, 449),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_flags", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, UNSIGNED_LONG}, 450),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_free", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, freefunc}, 451),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_getattr", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, getattrfunc}, 452),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_getattro", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, getattrofunc}, 453),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_itemsize", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, Py_ssize_t}, 454),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_iter", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, getiterfunc}, 455),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_iternext", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, iternextfunc}, 456),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_mro", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, PyObject}, 457),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_new", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, newfunc}, 458),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_setattr", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, setattrfunc}, 459),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_setattro", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, setattrofunc}, 460),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_subclasses", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, PyObject}, 461),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_traverse", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, traverseproc}, 462),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_vectorcall_offset", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, Py_ssize_t}, 463),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_weaklistoffset", Ignored, Void,
                                    new ArgDescriptor[]{PyTypeObject, Py_ssize_t}, 464),
                    new CApiBuiltinExecutable("Py_set_PyVarObject_ob_size", Ignored, Void,
                                    new ArgDescriptor[]{PyVarObject, Py_ssize_t}, 465),
                    new CApiBuiltinExecutable("_PyBytes_Join", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject}, 466),
                    new CApiBuiltinExecutable("_PyDict_Pop", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject}, 467),
                    new CApiBuiltinExecutable("_PyDict_SetItem_KnownHash", Direct, Int,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject, Py_hash_t}, 468),
                    new CApiBuiltinExecutable("_PyErr_BadInternalCall", Direct, Void,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, Int}, 469),
                    new CApiBuiltinExecutable("_PyErr_CreateAndSetException", Direct, Void,
                                    new ArgDescriptor[]{PyObject, PyObject}, 470),
                    new CApiBuiltinExecutable("_PyErr_WriteUnraisableMsg", Direct, Void,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, PyObject}, 471),
                    new CApiBuiltinExecutable("_PyList_Extend", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyListObject, PyObject}, 472),
                    new CApiBuiltinExecutable("_PyLong_Sign", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 473),
                    new CApiBuiltinExecutable("_PyNamespace_New", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 474),
                    new CApiBuiltinExecutable("_PyNumber_Index", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject}, 475),
                    new CApiBuiltinExecutable("_PyObject_Call1", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject, Int}, 476),
                    new CApiBuiltinExecutable("_PyObject_CallMethod1", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString, PyObject, Int}, 477),
                    new CApiBuiltinExecutable("_PyObject_Dump", Direct, Void,
                                    new ArgDescriptor[]{PyObjectWrapper}, 478),
                    new CApiBuiltinExecutable("_PyTraceMalloc_NewReference", Direct, Int,
                                    new ArgDescriptor[]{PyObject}, 479),
                    new CApiBuiltinExecutable("_PyTraceback_Add", Direct, Void,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int}, 480),
                    new CApiBuiltinExecutable("_PyTruffleBytes_Resize", Ignored, Int,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 481),
                    new CApiBuiltinExecutable("_PyTruffleErr_Warn", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, Py_ssize_t, PyObject}, 482),
                    new CApiBuiltinExecutable("_PyTruffleEval_EvalCodeEx", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, PyObject, PyObject, Pointer, Pointer, Pointer, PyObject, PyObject}, 483),
                    new CApiBuiltinExecutable("_PyTruffleModule_CreateInitialized_PyModule_New", Ignored, PyModuleObjectTransfer,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString}, 484),
                    new CApiBuiltinExecutable("_PyTruffleModule_GetAndIncMaxModuleNumber", Ignored, Py_ssize_t,
                                    new ArgDescriptor[]{}, 485),
                    new CApiBuiltinExecutable("_PyTruffleObject_MakeTpCall", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Pointer, Int, Pointer, Pointer}, 486),
                    new CApiBuiltinExecutable("_PyTruffleSet_NextEntry", Ignored, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, Py_ssize_t}, 487),
                    new CApiBuiltinExecutable("_PyTruffle_HashBytes", Ignored, Py_hash_t,
                                    new ArgDescriptor[]{ConstCharPtrAsTruffleString}, 488),
                    new CApiBuiltinExecutable("_PyTruffle_Trace_Free", Ignored, Int,
                                    new ArgDescriptor[]{Pointer, Py_ssize_t}, 489),
                    new CApiBuiltinExecutable("_PyType_Lookup", Direct, PyObjectBorrowed,
                                    new ArgDescriptor[]{PyTypeObject, PyObject}, 490),
                    new CApiBuiltinExecutable("_PyUnicode_AsASCIIString", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 491),
                    new CApiBuiltinExecutable("_PyUnicode_AsLatin1String", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 492),
                    new CApiBuiltinExecutable("_PyUnicode_AsUTF8String", Direct, PyObjectTransfer,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 493),
                    new CApiBuiltinExecutable("_PyUnicode_EqualToASCIIString", Direct, Int,
                                    new ArgDescriptor[]{PyObject, ConstCharPtrAsTruffleString}, 494),
                    new CApiBuiltinExecutable("_Py_HashDouble", Direct, Py_hash_t,
                                    new ArgDescriptor[]{PyObject, Double}, 495),
    };

    static CApiBuiltinNode createBuiltinNode(int id) {
        switch (id) {
            case 0:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory._PyTruffleBytes_ResizeNodeGen.create();
            case 1:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyBytes_FromObjectNodeGen.create();
            case 2:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyBytes_SizeNodeGen.create();
            case 3:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextIterBuiltinsFactory.PyCallIter_NewNodeGen.create();
            case 4:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyCallable_CheckNodeGen.create();
            case 5:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_GetContextNodeGen.create();
            case 6:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_GetDestructorNodeGen.create();
            case 7:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_GetNameNodeGen.create();
            case 8:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_GetPointerNodeGen.create();
            case 9:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_ImportNodeGen.create();
            case 10:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_IsValidNodeGen.create();
            case 11:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_NewNodeGen.create();
            case 12:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_SetContextNodeGen.create();
            case 13:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_SetDestructorNodeGen.create();
            case 14:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_SetNameNodeGen.create();
            case 15:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_SetPointerNodeGen.create();
            case 16:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFuncBuiltinsFactory.PyClassMethod_NewNodeGen.create();
            case 17:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_NewNodeGen.create();
            case 18:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_NewEmptyNodeGen.create();
            case 19:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_NewWithPosOnlyArgsNodeGen.create();
            case 20:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyComplex_FromDoublesNodeGen.create();
            case 21:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyComplex_ImagAsDoubleNodeGen.create();
            case 22:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyComplex_RealAsDoubleNodeGen.create();
            case 23:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextContextBuiltinsFactory.PyContextVar_NewNodeGen.create();
            case 24:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextContextBuiltinsFactory.PyContextVar_SetNodeGen.create();
            case 25:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsFactory.PyDictProxy_NewNodeGen.create();
            case 26:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_ClearNodeGen.create();
            case 27:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_ContainsNodeGen.create();
            case 28:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_CopyNodeGen.create();
            case 29:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_DelItemNodeGen.create();
            case 30:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_GetItemNodeGen.create();
            case 31:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_GetItemWithErrorNodeGen.create();
            case 32:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_KeysNodeGen.create();
            case 33:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_MergeNodeGen.create();
            case 34:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_NewNodeGen.create();
            case 35:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_SetDefaultNodeGen.create();
            case 36:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_SetItemNodeGen.create();
            case 37:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_SizeNodeGen.create();
            case 38:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_UpdateNodeGen.create();
            case 39:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_ValuesNodeGen.create();
            case 40:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_DisplayNodeGen.create();
            case 41:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_GivenExceptionMatchesNodeGen.create();
            case 42:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_NewExceptionNodeGen.create();
            case 43:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_NewExceptionWithDocNodeGen.create();
            case 44:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_OccurredNodeGen.create();
            case 45:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_PrintExNodeGen.create();
            case 46:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_RestoreNodeGen.create();
            case 47:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_SetExcInfoNodeGen.create();
            case 48:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyEval_GetBuiltinsNodeGen.create();
            case 49:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyEval_RestoreThreadFactory.create();
            case 50:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyEval_SaveThreadFactory.create();
            case 51:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_GetContextNodeGen.create();
            case 52:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_SetCauseNodeGen.create();
            case 53:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_SetContextNodeGen.create();
            case 54:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_SetTracebackNodeGen.create();
            case 55:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFileBuiltinsFactory.PyFile_WriteObjectNodeGen.create();
            case 56:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFloatBuiltinsFactory.PyFloat_AsDoubleNodeGen.create();
            case 57:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFloatBuiltinsFactory.PyFloat_FromDoubleNodeGen.create();
            case 58:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyFrame_NewFactory.create();
            case 59:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PyFrozenSet_NewNodeGen.create();
            case 60:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyGILState_EnsureFactory.create();
            case 61:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyGILState_ReleaseFactory.create();
            case 62:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_GetModuleDictNodeGen.create();
            case 63:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleNodeGen.create();
            case 64:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleNodeGen.create();
            case 65:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleLevelObjectNodeGen.create();
            case 66:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleNodeGen.create();
            case 67:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyIndex_CheckNodeGen.create();
            case 68:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextClassBuiltinsFactory.PyInstanceMethod_NewNodeGen.create();
            case 69:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyIter_NextNodeGen.create();
            case 70:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_AppendNodeGen.create();
            case 71:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_AsTupleNodeGen.create();
            case 72:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_GetItemNodeGen.create();
            case 73:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_GetSliceNodeGen.create();
            case 74:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_InsertNodeGen.create();
            case 75:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_NewNodeGen.create();
            case 76:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_ReverseNodeGen.create();
            case 77:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SetItemNodeGen.create();
            case 78:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SetSliceNodeGen.create();
            case 79:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SizeNodeGen.create();
            case 80:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SortNodeGen.create();
            case 81:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_AsVoidPtrNodeGen.create();
            case 82:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromDoubleNodeGen.create();
            case 83:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongNodeGen.create();
            case 84:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongNodeGen.create();
            case 85:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongNodeGen.create();
            case 86:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongNodeGen.create();
            case 87:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromUnsignedLongLongNodeGen.create();
            case 88:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromUnsignedLongLongNodeGen.create();
            case 89:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_CheckNodeGen.create();
            case 90:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_ItemsNodeGen.create();
            case 91:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_KeysNodeGen.create();
            case 92:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_SizeNodeGen.create();
            case 93:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_ValuesNodeGen.create();
            case 94:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextMemoryViewBuiltinsFactory.PyMemoryView_FromObjectNodeGen.create();
            case 95:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextMemoryViewBuiltinsFactory.PyMemoryView_GetContiguousNodeGen.create();
            case 96:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextClassBuiltinsFactory.PyMethod_NewNodeGen.create();
            case 97:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_AddIntConstantNodeGen.create();
            case 98:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_AddObjectRefNodeGen.create();
            case 99:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_GetNameObjectNodeGen.create();
            case 100:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_NewObjectNodeGen.create();
            case 101:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_NewObjectNodeGen.create();
            case 102:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_SetDocStringNodeGen.create();
            case 103:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_AbsoluteNodeGen.create();
            case 104:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_CheckNodeGen.create();
            case 105:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_DivmodNodeGen.create();
            case 106:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_FloatNodeGen.create();
            case 107:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_InPlacePowerNodeGen.create();
            case 108:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_IndexNodeGen.create();
            case 109:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_LongNodeGen.create();
            case 110:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_PowerNodeGen.create();
            case 111:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_ToBaseNodeGen.create();
            case 112:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPosixmoduleBuiltinsFactory.PyOS_FSPathNodeGen.create();
            case 113:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_ASCIINodeGen.create();
            case 114:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_AsFileDescriptorNodeGen.create();
            case 115:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_BytesNodeGen.create();
            case 116:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWeakrefBuiltinsFactory.PyObject_ClearWeakRefsNodeGen.create();
            case 117:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_DelItemNodeGen.create();
            case 118:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_DirNodeGen.create();
            case 119:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_FormatNodeGen.create();
            case 120:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyObject_GC_TrackFactory.create();
            case 121:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyObject_GC_UnTrackFactory.create();
            case 122:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_GetDocNodeGen.create();
            case 123:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_GetItemNodeGen.create();
            case 124:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_GetIterNodeGen.create();
            case 125:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HasAttrNodeGen.create();
            case 126:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HasAttrNodeGen.create();
            case 127:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HashNodeGen.create();
            case 128:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HashNotImplementedNodeGen.create();
            case 129:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_IsInstanceNodeGen.create();
            case 130:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_IsSubclassNodeGen.create();
            case 131:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_IsTrueNodeGen.create();
            case 132:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_LengthHintNodeGen.create();
            case 133:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_ReprNodeGen.create();
            case 134:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_RichCompareNodeGen.create();
            case 135:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_SetDocNodeGen.create();
            case 136:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_SetItemNodeGen.create();
            case 137:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_SizeNodeGen.create();
            case 138:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_StrNodeGen.create();
            case 139:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_TypeNodeGen.create();
            case 140:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPythonRunBuiltinsFactory.PyRun_StringFlagsNodeGen.create();
            case 141:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextIterBuiltinsFactory.PySeqIter_NewNodeGen.create();
            case 142:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_CheckNodeGen.create();
            case 143:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_ConcatNodeGen.create();
            case 144:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_ContainsNodeGen.create();
            case 145:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_DelItemNodeGen.create();
            case 146:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_GetItemNodeGen.create();
            case 147:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_GetSliceNodeGen.create();
            case 148:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_InPlaceConcatNodeGen.create();
            case 149:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_InPlaceRepeatNodeGen.create();
            case 150:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_SizeNodeGen.create();
            case 151:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_ListNodeGen.create();
            case 152:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_RepeatNodeGen.create();
            case 153:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_SetItemNodeGen.create();
            case 154:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_SizeNodeGen.create();
            case 155:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_TupleNodeGen.create();
            case 156:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_AddNodeGen.create();
            case 157:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_ClearNodeGen.create();
            case 158:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_ContainsNodeGen.create();
            case 159:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_DiscardNodeGen.create();
            case 160:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_NewNodeGen.create();
            case 161:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_PopNodeGen.create();
            case 162:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_SizeNodeGen.create();
            case 163:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSliceBuiltinsFactory.PySlice_NewNodeGen.create();
            case 164:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFuncBuiltinsFactory.PyStaticMethod_NewNodeGen.create();
            case 165:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextStructSeqBuiltinsFactory.PyStructSequence_NewNodeGen.create();
            case 166:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSysBuiltinsFactory.PySys_GetObjectNodeGen.create();
            case 167:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyThreadState_GetNodeGen.create();
            case 168:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyThreadState_GetDictNodeGen.create();
            case 169:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyThread_acquire_lockNodeGen.create();
            case 170:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyThread_allocate_lockNodeGen.create();
            case 171:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyThread_release_lockNodeGen.create();
            case 172:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTracebackBuiltinsFactory.PyTraceBack_HereNodeGen.create();
            case 173:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTraceMalloc_TrackFactory.create();
            case 174:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTraceMalloc_UntrackFactory.create();
            case 175:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleByteArray_FromStringAndSizeNodeGen.create();
            case 176:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleBytes_ConcatNodeGen.create();
            case 177:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleBytes_FromFormatNodeGen.create();
            case 178:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleBytes_FromStringAndSizeNodeGen.create();
            case 179:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextMethodBuiltinsFactory.PyTruffleCMethod_NewExNodeGen.create();
            case 180:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyTruffleComplex_AsCComplexNodeGen.create();
            case 181:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextContextBuiltinsFactory.PyTruffleContextVar_GetNodeGen.create();
            case 182:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsFactory.PyTruffleDescr_NewClassMethodNodeGen.create();
            case 183:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsFactory.PyTruffleDescr_NewGetSetNodeGen.create();
            case 184:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyTruffleDict_NextNodeGen.create();
            case 185:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyTruffleErr_FetchNodeGen.create();
            case 186:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyTruffleErr_GetExcInfoNodeGen.create();
            case 187:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory.PyTruffleHash_InitSecretNodeGen.create();
            case 188:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_AsPrimitiveNodeGen.create();
            case 189:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_FromStringNodeGen.create();
            case 190:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_OneNodeGen.create();
            case 191:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_ZeroNodeGen.create();
            case 192:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleModule_AddFunctionToModuleFactory.create();
            case 193:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyTruffleNumber_BinOpNodeGen.create();
            case 194:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyTruffleNumber_InPlaceBinOpNodeGen.create();
            case 195:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyTruffleNumber_UnaryOpNodeGen.create();
            case 196:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_CallFunctionObjArgsNodeGen.create();
            case 197:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_CallMethodObjArgsNodeGen.create();
            case 198:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_GenericGetAttrNodeGen.create();
            case 199:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_GenericSetAttrNodeGen.create();
            case 200:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_GetItemNodeGen.create();
            case 201:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyTruffleState_FindModuleNodeGen.create();
            case 202:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextStructSeqBuiltinsFactory.PyTruffleStructSequence_InitType2NodeGen.create();
            case 203:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextStructSeqBuiltinsFactory.PyTruffleStructSequence_NewTypeNodeGen.create();
            case 204:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleToCharPointerFactory.create();
            case 205:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleType_AddFunctionToTypeFactory.create();
            case 206:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleType_AddGetSetFactory.create();
            case 207:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleType_AddMemberFactory.create();
            case 208:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleType_AddSlotFactory.create();
            case 209:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_DecodeNodeGen.create();
            case 210:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_DecodeUTF8StatefulNodeGen.create();
            case 211:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_InternInPlaceNodeGen.create();
            case 212:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_NewNodeGen.create();
            case 213:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Arg_ParseTupleAndKeywordsFactory.create();
            case 214:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_ByteArray_EmptyWithCapacityFactory.create();
            case 215:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Bytes_AsStringFactory.create();
            case 216:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Bytes_CheckEmbeddedNullFactory.create();
            case 217:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Bytes_EmptyWithCapacityFactory.create();
            case 218:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Compute_MroFactory.create();
            case 219:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_DebugFactory.create();
            case 220:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_DebugTraceFactory.create();
            case 221:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSliceBuiltinsFactory.PyTruffle_EllipsisNodeGen.create();
            case 222:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBoolBuiltinsFactory.PyTruffle_FalseNodeGen.create();
            case 223:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_FatalErrorFuncFactory.create();
            case 224:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_FileSystemDefaultEncodingFactory.create();
            case 225:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Get_Inherited_Native_SlotsFactory.create();
            case 226:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory.PyTruffle_HashConstantNodeGen.create();
            case 227:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_LogStringFactory.create();
            case 228:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_MemoryViewFromBufferFactory.create();
            case 229:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Native_OptionsFactory.create();
            case 230:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_NewTypeDictFactory.create();
            case 231:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffle_NoValueNodeGen.create();
            case 232:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffle_NoneNodeGen.create();
            case 233:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffle_NotImplementedNodeGen.create();
            case 234:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_OS_DoubleToStringFactory.create();
            case 235:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_OS_StringToDoubleFactory.create();
            case 236:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Object_AllocFactory.create();
            case 237:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Object_FreeFactory.create();
            case 238:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Register_NULLFactory.create();
            case 239:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Set_Native_SlotsFactory.create();
            case 240:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Set_SulongTypeFactory.create();
            case 241:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_ToNativeFactory.create();
            case 242:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Trace_TypeFactory.create();
            case 243:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBoolBuiltinsFactory.PyTruffle_TrueNodeGen.create();
            case 244:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_TypeFactory.create();
            case 245:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Type_ModifiedFactory.create();
            case 246:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_AsUnicodeAndSizeNodeGen.create();
            case 247:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_AsWideCharNodeGen.create();
            case 248:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_DecodeUTF32NodeGen.create();
            case 249:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Unicode_FromFormatFactory.create();
            case 250:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_FromWcharNodeGen.create();
            case 251:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_tss_createFactory.create();
            case 252:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_tss_deleteFactory.create();
            case 253:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_tss_getFactory.create();
            case 254:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_tss_setFactory.create();
            case 255:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_GetItemNodeGen.create();
            case 256:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_GetSliceNodeGen.create();
            case 257:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_NewNodeGen.create();
            case 258:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_SetItemNodeGen.create();
            case 259:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_SizeNodeGen.create();
            case 260:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyType_IsSubtypeNodeGen.create();
            case 261:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_AsEncodedStringNodeGen.create();
            case 262:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_AsUnicodeEscapeStringNodeGen.create();
            case 263:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_CompareNodeGen.create();
            case 264:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ConcatNodeGen.create();
            case 265:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ContainsNodeGen.create();
            case 266:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_DecodeFSDefaultNodeGen.create();
            case 267:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_EncodeFSDefaultNodeGen.create();
            case 268:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FindCharNodeGen.create();
            case 269:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FormatNodeGen.create();
            case 270:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromEncodedObjectNodeGen.create();
            case 271:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromObjectNodeGen.create();
            case 272:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromOrdinalNodeGen.create();
            case 273:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromStringNodeGen.create();
            case 274:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_JoinNodeGen.create();
            case 275:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ReadCharNodeGen.create();
            case 276:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ReplaceNodeGen.create();
            case 277:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_SplitNodeGen.create();
            case 278:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_SubstringNodeGen.create();
            case 279:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_TailmatchNodeGen.create();
            case 280:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWeakrefBuiltinsFactory.PyWeakref_GetObjectNodeGen.create();
            case 281:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWeakrefBuiltinsFactory.PyWeakref_NewRefNodeGen.create();
            case 282:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyLifecycleBuiltinsFactory.Py_AtExitNodeGen.create();
            case 283:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextGenericAliasBuiltinsFactory.Py_GenericAliasNodeGen.create();
            case 284:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_lengthNodeGen.create();
            case 285:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_asciiNodeGen.create();
            case 286:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_compactNodeGen.create();
            case 287:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_kindNodeGen.create();
            case 288:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_readyNodeGen.create();
            case 289:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_wstrNodeGen.create();
            case 290:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 291:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 292:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 293:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 294:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 295:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyByteArrayObject_ob_exportsNodeGen.create();
            case 296:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyByteArrayObject_ob_startNodeGen.create();
            case 297:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_mlNodeGen.create();
            case 298:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_moduleNodeGen.create();
            case 299:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_selfNodeGen.create();
            case 300:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_weakreflistNodeGen.create();
            case 301:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_vectorcallNodeGen.create();
            case 302:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCMethodObject_mm_classNodeGen.create();
            case 303:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCompactUnicodeObject_wstr_lengthNodeGen.create();
            case 304:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyDescrObject_d_nameNodeGen.create();
            case 305:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyDescrObject_d_typeNodeGen.create();
            case 306:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyFrameObject_f_linenoNodeGen.create();
            case 307:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_closureNodeGen.create();
            case 308:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_docNodeGen.create();
            case 309:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_getNodeGen.create();
            case 310:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_nameNodeGen.create();
            case 311:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_setNodeGen.create();
            case 312:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyInstanceMethodObject_funcNodeGen.create();
            case 313:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PSequence_ob_itemNodeGen.create();
            case 314:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyLongObject_ob_digitNodeGen.create();
            case 315:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMappingMethods_mp_ass_subscriptNodeGen.create();
            case 316:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMappingMethods_mp_lengthNodeGen.create();
            case 317:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMappingMethods_mp_subscriptNodeGen.create();
            case 318:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_docNodeGen.create();
            case 319:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_flagsNodeGen.create();
            case 320:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_methNodeGen.create();
            case 321:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_nameNodeGen.create();
            case 322:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDescrObject_d_methodNodeGen.create();
            case 323:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodObject_im_funcNodeGen.create();
            case 324:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodObject_im_selfNodeGen.create();
            case 325:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_docNodeGen.create();
            case 326:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_methodsNodeGen.create();
            case 327:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_nameNodeGen.create();
            case 328:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_sizeNodeGen.create();
            case 329:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleObject_md_defNodeGen.create();
            case 330:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleObject_md_dictNodeGen.create();
            case 331:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleObject_md_stateNodeGen.create();
            case 332:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_absoluteNodeGen.create();
            case 333:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_addNodeGen.create();
            case 334:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_andNodeGen.create();
            case 335:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_boolNodeGen.create();
            case 336:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_divmodNodeGen.create();
            case 337:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_floatNodeGen.create();
            case 338:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_floor_divideNodeGen.create();
            case 339:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_indexNodeGen.create();
            case 340:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_addNodeGen.create();
            case 341:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_andNodeGen.create();
            case 342:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_floor_divideNodeGen.create();
            case 343:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_lshiftNodeGen.create();
            case 344:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 345:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_multiplyNodeGen.create();
            case 346:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_orNodeGen.create();
            case 347:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_powerNodeGen.create();
            case 348:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_remainderNodeGen.create();
            case 349:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_rshiftNodeGen.create();
            case 350:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_subtractNodeGen.create();
            case 351:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_true_divideNodeGen.create();
            case 352:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_xorNodeGen.create();
            case 353:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_intNodeGen.create();
            case 354:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_invertNodeGen.create();
            case 355:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_lshiftNodeGen.create();
            case 356:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 357:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_multiplyNodeGen.create();
            case 358:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_negativeNodeGen.create();
            case 359:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_orNodeGen.create();
            case 360:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_positiveNodeGen.create();
            case 361:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_powerNodeGen.create();
            case 362:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_remainderNodeGen.create();
            case 363:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_rshiftNodeGen.create();
            case 364:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_subtractNodeGen.create();
            case 365:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_true_divideNodeGen.create();
            case 366:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_xorNodeGen.create();
            case 367:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyObject_ob_refcntNodeGen.create();
            case 368:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyObject_ob_typeNodeGen.create();
            case 369:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 370:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySequenceMethods_sq_concatNodeGen.create();
            case 371:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 372:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 373:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 374:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySequenceMethods_sq_itemNodeGen.create();
            case 375:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 376:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySequenceMethods_sq_repeatNodeGen.create();
            case 377:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySetObject_usedNodeGen.create();
            case 378:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySliceObject_startNodeGen.create();
            case 379:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySliceObject_stepNodeGen.create();
            case 380:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySliceObject_stopNodeGen.create();
            case 381:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyThreadState_dictNodeGen.create();
            case 382:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PSequence_ob_itemNodeGen.create();
            case 383:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_allocNodeGen.create();
            case 384:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_asyncNodeGen.create();
            case 385:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_bufferNodeGen.create();
            case 386:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_mappingNodeGen.create();
            case 387:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_numberNodeGen.create();
            case 388:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_sequenceNodeGen.create();
            case 389:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_baseNodeGen.create();
            case 390:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPyPtrNodeGen.create();
            case 391:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_basicsizeNodeGen.create();
            case 392:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPyPtrNodeGen.create();
            case 393:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_callNodeGen.create();
            case 394:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_TraverseClearNodeGen.create();
            case 395:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_deallocNodeGen.create();
            case 396:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_delNodeGen.create();
            case 397:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 398:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 399:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_dictNodeGen.create();
            case 400:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_dictoffsetNodeGen.create();
            case 401:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_docNodeGen.create();
            case 402:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 403:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_flagsNodeGen.create();
            case 404:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_freeNodeGen.create();
            case 405:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 406:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_getattroNodeGen.create();
            case 407:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 408:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_hashNodeGen.create();
            case 409:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_initNodeGen.create();
            case 410:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 411:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_itemsizeNodeGen.create();
            case 412:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_iterNodeGen.create();
            case 413:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_iternextNodeGen.create();
            case 414:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 415:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 416:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_mroNodeGen.create();
            case 417:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_nameNodeGen.create();
            case 418:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_newNodeGen.create();
            case 419:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_reprNodeGen.create();
            case 420:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_richcompareNodeGen.create();
            case 421:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 422:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_setattroNodeGen.create();
            case 423:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_strNodeGen.create();
            case 424:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_subclassesNodeGen.create();
            case 425:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_TraverseClearNodeGen.create();
            case 426:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 427:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_vectorcall_offsetNodeGen.create();
            case 428:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_version_tagNodeGen.create();
            case 429:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPyPtrNodeGen.create();
            case 430:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_weaklistoffsetNodeGen.create();
            case 431:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyUnicodeObject_dataNodeGen.create();
            case 432:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyVarObject_ob_sizeNodeGen.create();
            case 433:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 434:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_mmap_object_dataNodeGen.create();
            case 435:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyByteArrayObject_ob_exportsNodeGen.create();
            case 436:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyFrameObject_f_linenoNodeGen.create();
            case 437:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyModuleObject_md_defNodeGen.create();
            case 438:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyModuleObject_md_stateNodeGen.create();
            case 439:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyObject_ob_refcntNodeGen.create();
            case 440:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_allocNodeGen.create();
            case 441:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_as_bufferNodeGen.create();
            case 442:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 443:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 444:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_basicsizeNodeGen.create();
            case 445:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 446:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_deallocNodeGen.create();
            case 447:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_dictNodeGen.create();
            case 448:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_dictoffsetNodeGen.create();
            case 449:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 450:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_flagsNodeGen.create();
            case 451:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_freeNodeGen.create();
            case 452:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 453:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 454:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_itemsizeNodeGen.create();
            case 455:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 456:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 457:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 458:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 459:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 460:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 461:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_subclassesNodeGen.create();
            case 462:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 463:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_vectorcall_offsetNodeGen.create();
            case 464:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 465:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 466:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory._PyBytes_JoinNodeGen.create();
            case 467:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory._PyDict_PopNodeGen.create();
            case 468:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory._PyDict_SetItem_KnownHashNodeGen.create();
            case 469:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory._PyErr_BadInternalCallNodeGen.create();
            case 470:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory._PyErr_CreateAndSetExceptionNodeGen.create();
            case 471:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory._PyErr_WriteUnraisableMsgNodeGen.create();
            case 472:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory._PyList_ExtendNodeGen.create();
            case 473:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory._PyLong_SignNodeGen.create();
            case 474:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextNamespaceBuiltinsFactory._PyNamespace_NewNodeGen.create();
            case 475:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_IndexNodeGen.create();
            case 476:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyObject_Call1NodeGen.create();
            case 477:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyObject_CallMethod1NodeGen.create();
            case 478:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyObject_DumpNodeGen.create();
            case 479:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory._PyTraceMalloc_NewReferenceFactory.create();
            case 480:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTracebackBuiltinsFactory._PyTraceback_AddNodeGen.create();
            case 481:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory._PyTruffleBytes_ResizeNodeGen.create();
            case 482:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWarnBuiltinsFactory._PyTruffleErr_WarnNodeGen.create();
            case 483:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory._PyTruffleEval_EvalCodeExNodeGen.create();
            case 484:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory._PyTruffleModule_CreateInitialized_PyModule_NewNodeGen.create();
            case 485:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory._PyTruffleModule_GetAndIncMaxModuleNumberNodeGen.create();
            case 486:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyTruffleObject_MakeTpCallNodeGen.create();
            case 487:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory._PyTruffleSet_NextEntryNodeGen.create();
            case 488:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory._PyTruffle_HashBytesNodeGen.create();
            case 489:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory._PyTruffle_Trace_FreeFactory.create();
            case 490:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory._PyType_LookupNodeGen.create();
            case 491:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._PyUnicode_AsASCIIStringNodeGen.create();
            case 492:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._PyUnicode_AsLatin1StringNodeGen.create();
            case 493:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._PyUnicode_AsUTF8StringNodeGen.create();
            case 494:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_CompareNodeGen.create();
            case 495:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory._Py_HashDoubleNodeGen.create();
        }
        return null;
    }

    public static CApiBuiltinExecutable getSlot(String key) {
        switch (key) {
            case "PyASCIIObject_length":
                return builtins[284];
            case "PyASCIIObject_state_ascii":
                return builtins[285];
            case "PyASCIIObject_state_compact":
                return builtins[286];
            case "PyASCIIObject_state_kind":
                return builtins[287];
            case "PyASCIIObject_state_ready":
                return builtins[288];
            case "PyASCIIObject_wstr":
                return builtins[289];
            case "PyAsyncMethods_am_aiter":
                return builtins[290];
            case "PyAsyncMethods_am_anext":
                return builtins[291];
            case "PyAsyncMethods_am_await":
                return builtins[292];
            case "PyBufferProcs_bf_getbuffer":
                return builtins[293];
            case "PyBufferProcs_bf_releasebuffer":
                return builtins[294];
            case "PyByteArrayObject_ob_exports":
                return builtins[295];
            case "PyByteArrayObject_ob_start":
                return builtins[296];
            case "PyCFunctionObject_m_ml":
                return builtins[297];
            case "PyCFunctionObject_m_module":
                return builtins[298];
            case "PyCFunctionObject_m_self":
                return builtins[299];
            case "PyCFunctionObject_m_weakreflist":
                return builtins[300];
            case "PyCFunctionObject_vectorcall":
                return builtins[301];
            case "PyCMethodObject_mm_class":
                return builtins[302];
            case "PyCompactUnicodeObject_wstr_length":
                return builtins[303];
            case "PyDescrObject_d_name":
                return builtins[304];
            case "PyDescrObject_d_type":
                return builtins[305];
            case "PyFrameObject_f_lineno":
                return builtins[306];
            case "PyGetSetDef_closure":
                return builtins[307];
            case "PyGetSetDef_doc":
                return builtins[308];
            case "PyGetSetDef_get":
                return builtins[309];
            case "PyGetSetDef_name":
                return builtins[310];
            case "PyGetSetDef_set":
                return builtins[311];
            case "PyInstanceMethodObject_func":
                return builtins[312];
            case "PyListObject_ob_item":
                return builtins[313];
            case "PyLongObject_ob_digit":
                return builtins[314];
            case "PyMappingMethods_mp_ass_subscript":
                return builtins[315];
            case "PyMappingMethods_mp_length":
                return builtins[316];
            case "PyMappingMethods_mp_subscript":
                return builtins[317];
            case "PyMethodDef_ml_doc":
                return builtins[318];
            case "PyMethodDef_ml_flags":
                return builtins[319];
            case "PyMethodDef_ml_meth":
                return builtins[320];
            case "PyMethodDef_ml_name":
                return builtins[321];
            case "PyMethodDescrObject_d_method":
                return builtins[322];
            case "PyMethodObject_im_func":
                return builtins[323];
            case "PyMethodObject_im_self":
                return builtins[324];
            case "PyModuleDef_m_doc":
                return builtins[325];
            case "PyModuleDef_m_methods":
                return builtins[326];
            case "PyModuleDef_m_name":
                return builtins[327];
            case "PyModuleDef_m_size":
                return builtins[328];
            case "PyModuleObject_md_def":
                return builtins[329];
            case "PyModuleObject_md_dict":
                return builtins[330];
            case "PyModuleObject_md_state":
                return builtins[331];
            case "PyNumberMethods_nb_absolute":
                return builtins[332];
            case "PyNumberMethods_nb_add":
                return builtins[333];
            case "PyNumberMethods_nb_and":
                return builtins[334];
            case "PyNumberMethods_nb_bool":
                return builtins[335];
            case "PyNumberMethods_nb_divmod":
                return builtins[336];
            case "PyNumberMethods_nb_float":
                return builtins[337];
            case "PyNumberMethods_nb_floor_divide":
                return builtins[338];
            case "PyNumberMethods_nb_index":
                return builtins[339];
            case "PyNumberMethods_nb_inplace_add":
                return builtins[340];
            case "PyNumberMethods_nb_inplace_and":
                return builtins[341];
            case "PyNumberMethods_nb_inplace_floor_divide":
                return builtins[342];
            case "PyNumberMethods_nb_inplace_lshift":
                return builtins[343];
            case "PyNumberMethods_nb_inplace_matrix_multiply":
                return builtins[344];
            case "PyNumberMethods_nb_inplace_multiply":
                return builtins[345];
            case "PyNumberMethods_nb_inplace_or":
                return builtins[346];
            case "PyNumberMethods_nb_inplace_power":
                return builtins[347];
            case "PyNumberMethods_nb_inplace_remainder":
                return builtins[348];
            case "PyNumberMethods_nb_inplace_rshift":
                return builtins[349];
            case "PyNumberMethods_nb_inplace_subtract":
                return builtins[350];
            case "PyNumberMethods_nb_inplace_true_divide":
                return builtins[351];
            case "PyNumberMethods_nb_inplace_xor":
                return builtins[352];
            case "PyNumberMethods_nb_int":
                return builtins[353];
            case "PyNumberMethods_nb_invert":
                return builtins[354];
            case "PyNumberMethods_nb_lshift":
                return builtins[355];
            case "PyNumberMethods_nb_matrix_multiply":
                return builtins[356];
            case "PyNumberMethods_nb_multiply":
                return builtins[357];
            case "PyNumberMethods_nb_negative":
                return builtins[358];
            case "PyNumberMethods_nb_or":
                return builtins[359];
            case "PyNumberMethods_nb_positive":
                return builtins[360];
            case "PyNumberMethods_nb_power":
                return builtins[361];
            case "PyNumberMethods_nb_remainder":
                return builtins[362];
            case "PyNumberMethods_nb_rshift":
                return builtins[363];
            case "PyNumberMethods_nb_subtract":
                return builtins[364];
            case "PyNumberMethods_nb_true_divide":
                return builtins[365];
            case "PyNumberMethods_nb_xor":
                return builtins[366];
            case "PyObject_ob_refcnt":
                return builtins[367];
            case "PyObject_ob_type":
                return builtins[368];
            case "PySequenceMethods_sq_ass_item":
                return builtins[369];
            case "PySequenceMethods_sq_concat":
                return builtins[370];
            case "PySequenceMethods_sq_contains":
                return builtins[371];
            case "PySequenceMethods_sq_inplace_concat":
                return builtins[372];
            case "PySequenceMethods_sq_inplace_repeat":
                return builtins[373];
            case "PySequenceMethods_sq_item":
                return builtins[374];
            case "PySequenceMethods_sq_length":
                return builtins[375];
            case "PySequenceMethods_sq_repeat":
                return builtins[376];
            case "PySetObject_used":
                return builtins[377];
            case "PySliceObject_start":
                return builtins[378];
            case "PySliceObject_step":
                return builtins[379];
            case "PySliceObject_stop":
                return builtins[380];
            case "PyThreadState_dict":
                return builtins[381];
            case "PyTupleObject_ob_item":
                return builtins[382];
            case "PyTypeObject_tp_alloc":
                return builtins[383];
            case "PyTypeObject_tp_as_async":
                return builtins[384];
            case "PyTypeObject_tp_as_buffer":
                return builtins[385];
            case "PyTypeObject_tp_as_mapping":
                return builtins[386];
            case "PyTypeObject_tp_as_number":
                return builtins[387];
            case "PyTypeObject_tp_as_sequence":
                return builtins[388];
            case "PyTypeObject_tp_base":
                return builtins[389];
            case "PyTypeObject_tp_bases":
                return builtins[390];
            case "PyTypeObject_tp_basicsize":
                return builtins[391];
            case "PyTypeObject_tp_cache":
                return builtins[392];
            case "PyTypeObject_tp_call":
                return builtins[393];
            case "PyTypeObject_tp_clear":
                return builtins[394];
            case "PyTypeObject_tp_dealloc":
                return builtins[395];
            case "PyTypeObject_tp_del":
                return builtins[396];
            case "PyTypeObject_tp_descr_get":
                return builtins[397];
            case "PyTypeObject_tp_descr_set":
                return builtins[398];
            case "PyTypeObject_tp_dict":
                return builtins[399];
            case "PyTypeObject_tp_dictoffset":
                return builtins[400];
            case "PyTypeObject_tp_doc":
                return builtins[401];
            case "PyTypeObject_tp_finalize":
                return builtins[402];
            case "PyTypeObject_tp_flags":
                return builtins[403];
            case "PyTypeObject_tp_free":
                return builtins[404];
            case "PyTypeObject_tp_getattr":
                return builtins[405];
            case "PyTypeObject_tp_getattro":
                return builtins[406];
            case "PyTypeObject_tp_getset":
                return builtins[407];
            case "PyTypeObject_tp_hash":
                return builtins[408];
            case "PyTypeObject_tp_init":
                return builtins[409];
            case "PyTypeObject_tp_is_gc":
                return builtins[410];
            case "PyTypeObject_tp_itemsize":
                return builtins[411];
            case "PyTypeObject_tp_iter":
                return builtins[412];
            case "PyTypeObject_tp_iternext":
                return builtins[413];
            case "PyTypeObject_tp_members":
                return builtins[414];
            case "PyTypeObject_tp_methods":
                return builtins[415];
            case "PyTypeObject_tp_mro":
                return builtins[416];
            case "PyTypeObject_tp_name":
                return builtins[417];
            case "PyTypeObject_tp_new":
                return builtins[418];
            case "PyTypeObject_tp_repr":
                return builtins[419];
            case "PyTypeObject_tp_richcompare":
                return builtins[420];
            case "PyTypeObject_tp_setattr":
                return builtins[421];
            case "PyTypeObject_tp_setattro":
                return builtins[422];
            case "PyTypeObject_tp_str":
                return builtins[423];
            case "PyTypeObject_tp_subclasses":
                return builtins[424];
            case "PyTypeObject_tp_traverse":
                return builtins[425];
            case "PyTypeObject_tp_vectorcall":
                return builtins[426];
            case "PyTypeObject_tp_vectorcall_offset":
                return builtins[427];
            case "PyTypeObject_tp_version_tag":
                return builtins[428];
            case "PyTypeObject_tp_weaklist":
                return builtins[429];
            case "PyTypeObject_tp_weaklistoffset":
                return builtins[430];
            case "PyUnicodeObject_data":
                return builtins[431];
            case "PyVarObject_ob_size":
                return builtins[432];
            case "dummy":
                return builtins[433];
            case "mmap_object_data":
                return builtins[434];
        }
        return null;
    }
// {{end CAPI_BUILTINS}}
}
