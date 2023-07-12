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

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinExecutable;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;

public abstract class PythonCextBuiltinRegistry {

    private PythonCextBuiltinRegistry() {
        // no instances
    }

    // {{start CAPI_BUILTINS}}
    // GENERATED CODE - see CApiCodeGen
    // This can be re-generated using the 'mx python-capi-forwards' command or
    // by executing the main class CApiCodeGen

    // @formatter:off
    // Checkstyle: stop
    public static final CApiBuiltinExecutable PyByteArray_Resize = new CApiBuiltinExecutable("PyByteArray_Resize", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 0);
    public static final CApiBuiltinExecutable PyBytes_FromObject = new CApiBuiltinExecutable("PyBytes_FromObject", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 1);
    public static final CApiBuiltinExecutable PyBytes_Size = new CApiBuiltinExecutable("PyBytes_Size", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 2);
    public static final CApiBuiltinExecutable PyCallIter_New = new CApiBuiltinExecutable("PyCallIter_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 3);
    public static final CApiBuiltinExecutable PyCallable_Check = new CApiBuiltinExecutable("PyCallable_Check", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 4);
    public static final CApiBuiltinExecutable PyCapsule_GetContext = new CApiBuiltinExecutable("PyCapsule_GetContext", CApiCallPath.Direct, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 5);
    public static final CApiBuiltinExecutable PyCapsule_GetDestructor = new CApiBuiltinExecutable("PyCapsule_GetDestructor", CApiCallPath.Direct, ArgDescriptor.PY_CAPSULE_DESTRUCTOR, new ArgDescriptor[]{ArgDescriptor.PyObject}, 6);
    public static final CApiBuiltinExecutable PyCapsule_GetName = new CApiBuiltinExecutable("PyCapsule_GetName", CApiCallPath.Direct, ArgDescriptor.ConstCharPtr, new ArgDescriptor[]{ArgDescriptor.PyObject}, 7);
    public static final CApiBuiltinExecutable PyCapsule_GetPointer = new CApiBuiltinExecutable("PyCapsule_GetPointer", CApiCallPath.Direct, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 8);
    public static final CApiBuiltinExecutable PyCapsule_Import = new CApiBuiltinExecutable("PyCapsule_Import", CApiCallPath.Direct, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int}, 9);
    public static final CApiBuiltinExecutable PyCapsule_IsValid = new CApiBuiltinExecutable("PyCapsule_IsValid", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 10);
    public static final CApiBuiltinExecutable PyCapsule_New = new CApiBuiltinExecutable("PyCapsule_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PY_CAPSULE_DESTRUCTOR}, 11);
    public static final CApiBuiltinExecutable PyCapsule_SetContext = new CApiBuiltinExecutable("PyCapsule_SetContext", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Pointer}, 12);
    public static final CApiBuiltinExecutable PyCapsule_SetDestructor = new CApiBuiltinExecutable("PyCapsule_SetDestructor", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PY_CAPSULE_DESTRUCTOR}, 13);
    public static final CApiBuiltinExecutable PyCapsule_SetName = new CApiBuiltinExecutable("PyCapsule_SetName", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 14);
    public static final CApiBuiltinExecutable PyCapsule_SetPointer = new CApiBuiltinExecutable("PyCapsule_SetPointer", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Pointer}, 15);
    public static final CApiBuiltinExecutable PyClassMethod_New = new CApiBuiltinExecutable("PyClassMethod_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 16);
    public static final CApiBuiltinExecutable PyCode_Addr2Line = new CApiBuiltinExecutable("PyCode_Addr2Line", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyCodeObject, ArgDescriptor.Int}, 17);
    public static final CApiBuiltinExecutable PyCode_GetFileName = new CApiBuiltinExecutable("PyCode_GetFileName", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyCodeObject}, 18);
    public static final CApiBuiltinExecutable PyCode_GetName = new CApiBuiltinExecutable("PyCode_GetName", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyCodeObject}, 19);
    public static final CApiBuiltinExecutable PyCode_New = new CApiBuiltinExecutable("PyCode_New", CApiCallPath.Direct, ArgDescriptor.PyCodeObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Int, ArgDescriptor.PyObject}, 20);
    public static final CApiBuiltinExecutable PyCode_NewEmpty = new CApiBuiltinExecutable("PyCode_NewEmpty", CApiCallPath.Direct, ArgDescriptor.PyCodeObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int}, 21);
    public static final CApiBuiltinExecutable PyCode_NewWithPosOnlyArgs = new CApiBuiltinExecutable("PyCode_NewWithPosOnlyArgs", CApiCallPath.Direct, ArgDescriptor.PyCodeObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Int, ArgDescriptor.PyObject}, 22);
    public static final CApiBuiltinExecutable PyCodec_Decoder = new CApiBuiltinExecutable("PyCodec_Decoder", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 23);
    public static final CApiBuiltinExecutable PyCodec_Encoder = new CApiBuiltinExecutable("PyCodec_Encoder", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 24);
    public static final CApiBuiltinExecutable PyComplex_FromDoubles = new CApiBuiltinExecutable("PyComplex_FromDoubles", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Double, ArgDescriptor.Double}, 25);
    public static final CApiBuiltinExecutable PyComplex_ImagAsDouble = new CApiBuiltinExecutable("PyComplex_ImagAsDouble", CApiCallPath.Direct, ArgDescriptor.Double, new ArgDescriptor[]{ArgDescriptor.PyObject}, 26);
    public static final CApiBuiltinExecutable PyComplex_RealAsDouble = new CApiBuiltinExecutable("PyComplex_RealAsDouble", CApiCallPath.Direct, ArgDescriptor.Double, new ArgDescriptor[]{ArgDescriptor.PyObject}, 27);
    public static final CApiBuiltinExecutable PyContextVar_New = new CApiBuiltinExecutable("PyContextVar_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PyObject}, 28);
    public static final CApiBuiltinExecutable PyContextVar_Set = new CApiBuiltinExecutable("PyContextVar_Set", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 29);
    public static final CApiBuiltinExecutable PyDictProxy_New = new CApiBuiltinExecutable("PyDictProxy_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 30);
    public static final CApiBuiltinExecutable PyDict_Clear = new CApiBuiltinExecutable("PyDict_Clear", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObject}, 31);
    public static final CApiBuiltinExecutable PyDict_Contains = new CApiBuiltinExecutable("PyDict_Contains", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 32);
    public static final CApiBuiltinExecutable PyDict_Copy = new CApiBuiltinExecutable("PyDict_Copy", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 33);
    public static final CApiBuiltinExecutable PyDict_DelItem = new CApiBuiltinExecutable("PyDict_DelItem", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 34);
    public static final CApiBuiltinExecutable PyDict_GetItem = new CApiBuiltinExecutable("PyDict_GetItem", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 35);
    public static final CApiBuiltinExecutable PyDict_GetItemWithError = new CApiBuiltinExecutable("PyDict_GetItemWithError", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 36);
    public static final CApiBuiltinExecutable PyDict_Items = new CApiBuiltinExecutable("PyDict_Items", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 37);
    public static final CApiBuiltinExecutable PyDict_Keys = new CApiBuiltinExecutable("PyDict_Keys", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 38);
    public static final CApiBuiltinExecutable PyDict_Merge = new CApiBuiltinExecutable("PyDict_Merge", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Int}, 39);
    public static final CApiBuiltinExecutable PyDict_New = new CApiBuiltinExecutable("PyDict_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 40);
    public static final CApiBuiltinExecutable PyDict_SetDefault = new CApiBuiltinExecutable("PyDict_SetDefault", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 41);
    public static final CApiBuiltinExecutable PyDict_SetItem = new CApiBuiltinExecutable("PyDict_SetItem", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 42);
    public static final CApiBuiltinExecutable PyDict_Size = new CApiBuiltinExecutable("PyDict_Size", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 43);
    public static final CApiBuiltinExecutable PyDict_Update = new CApiBuiltinExecutable("PyDict_Update", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 44);
    public static final CApiBuiltinExecutable PyDict_Values = new CApiBuiltinExecutable("PyDict_Values", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 45);
    public static final CApiBuiltinExecutable PyErr_Display = new CApiBuiltinExecutable("PyErr_Display", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 46);
    public static final CApiBuiltinExecutable PyErr_GivenExceptionMatches = new CApiBuiltinExecutable("PyErr_GivenExceptionMatches", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 47);
    public static final CApiBuiltinExecutable PyErr_NewException = new CApiBuiltinExecutable("PyErr_NewException", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 48);
    public static final CApiBuiltinExecutable PyErr_NewExceptionWithDoc = new CApiBuiltinExecutable("PyErr_NewExceptionWithDoc", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 49);
    public static final CApiBuiltinExecutable PyErr_Occurred = new CApiBuiltinExecutable("PyErr_Occurred", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{}, 50);
    public static final CApiBuiltinExecutable PyErr_PrintEx = new CApiBuiltinExecutable("PyErr_PrintEx", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.Int}, 51);
    public static final CApiBuiltinExecutable PyErr_Restore = new CApiBuiltinExecutable("PyErr_Restore", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 52);
    public static final CApiBuiltinExecutable PyErr_SetExcInfo = new CApiBuiltinExecutable("PyErr_SetExcInfo", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 53);
    public static final CApiBuiltinExecutable PyEval_GetBuiltins = new CApiBuiltinExecutable("PyEval_GetBuiltins", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{}, 54);
    public static final CApiBuiltinExecutable PyEval_GetFrame = new CApiBuiltinExecutable("PyEval_GetFrame", CApiCallPath.Direct, ArgDescriptor.PyFrameObjectTransfer, new ArgDescriptor[]{}, 55);
    public static final CApiBuiltinExecutable PyEval_RestoreThread = new CApiBuiltinExecutable("PyEval_RestoreThread", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyThreadState}, 56);
    public static final CApiBuiltinExecutable PyEval_SaveThread = new CApiBuiltinExecutable("PyEval_SaveThread", CApiCallPath.Direct, ArgDescriptor.PyThreadState, new ArgDescriptor[]{}, 57);
    public static final CApiBuiltinExecutable PyException_GetCause = new CApiBuiltinExecutable("PyException_GetCause", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 58);
    public static final CApiBuiltinExecutable PyException_GetContext = new CApiBuiltinExecutable("PyException_GetContext", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 59);
    public static final CApiBuiltinExecutable PyException_SetCause = new CApiBuiltinExecutable("PyException_SetCause", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 60);
    public static final CApiBuiltinExecutable PyException_SetContext = new CApiBuiltinExecutable("PyException_SetContext", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 61);
    public static final CApiBuiltinExecutable PyException_SetTraceback = new CApiBuiltinExecutable("PyException_SetTraceback", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 62);
    public static final CApiBuiltinExecutable PyFile_WriteObject = new CApiBuiltinExecutable("PyFile_WriteObject", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Int}, 63);
    public static final CApiBuiltinExecutable PyFloat_FromDouble = new CApiBuiltinExecutable("PyFloat_FromDouble", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Double}, 64);
    public static final CApiBuiltinExecutable PyFloat_FromString = new CApiBuiltinExecutable("PyFloat_FromString", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObjectAsTruffleString}, 65);
    public static final CApiBuiltinExecutable PyFrame_GetBack = new CApiBuiltinExecutable("PyFrame_GetBack", CApiCallPath.Direct, ArgDescriptor.PyFrameObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyFrameObject}, 66);
    public static final CApiBuiltinExecutable PyFrame_GetBuiltins = new CApiBuiltinExecutable("PyFrame_GetBuiltins", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyFrameObject}, 67);
    public static final CApiBuiltinExecutable PyFrame_GetCode = new CApiBuiltinExecutable("PyFrame_GetCode", CApiCallPath.Direct, ArgDescriptor.PyCodeObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyFrameObject}, 68);
    public static final CApiBuiltinExecutable PyFrame_GetGlobals = new CApiBuiltinExecutable("PyFrame_GetGlobals", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyFrameObject}, 69);
    public static final CApiBuiltinExecutable PyFrame_GetLasti = new CApiBuiltinExecutable("PyFrame_GetLasti", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyFrameObject}, 70);
    public static final CApiBuiltinExecutable PyFrame_GetLineNumber = new CApiBuiltinExecutable("PyFrame_GetLineNumber", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyFrameObject}, 71);
    public static final CApiBuiltinExecutable PyFrame_GetLocals = new CApiBuiltinExecutable("PyFrame_GetLocals", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyFrameObject}, 72);
    public static final CApiBuiltinExecutable PyFrame_New = new CApiBuiltinExecutable("PyFrame_New", CApiCallPath.Direct, ArgDescriptor.PyFrameObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyThreadState, ArgDescriptor.PyCodeObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 73);
    public static final CApiBuiltinExecutable PyFrozenSet_New = new CApiBuiltinExecutable("PyFrozenSet_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 74);
    public static final CApiBuiltinExecutable PyGILState_Check = new CApiBuiltinExecutable("PyGILState_Check", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{}, 75);
    public static final CApiBuiltinExecutable PyImport_GetModuleDict = new CApiBuiltinExecutable("PyImport_GetModuleDict", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{}, 76);
    public static final CApiBuiltinExecutable PyImport_Import = new CApiBuiltinExecutable("PyImport_Import", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObjectAsTruffleString}, 77);
    public static final CApiBuiltinExecutable PyImport_ImportModule = new CApiBuiltinExecutable("PyImport_ImportModule", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 78);
    public static final CApiBuiltinExecutable PyImport_ImportModuleLevelObject = new CApiBuiltinExecutable("PyImport_ImportModuleLevelObject", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObjectAsTruffleString, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Int}, 79);
    public static final CApiBuiltinExecutable PyImport_ImportModuleNoBlock = new CApiBuiltinExecutable("PyImport_ImportModuleNoBlock", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 80);
    public static final CApiBuiltinExecutable PyIndex_Check = new CApiBuiltinExecutable("PyIndex_Check", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 81);
    public static final CApiBuiltinExecutable PyInstanceMethod_New = new CApiBuiltinExecutable("PyInstanceMethod_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 82);
    public static final CApiBuiltinExecutable PyIter_Check = new CApiBuiltinExecutable("PyIter_Check", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 83);
    public static final CApiBuiltinExecutable PyIter_Next = new CApiBuiltinExecutable("PyIter_Next", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 84);
    public static final CApiBuiltinExecutable PyList_Append = new CApiBuiltinExecutable("PyList_Append", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 85);
    public static final CApiBuiltinExecutable PyList_AsTuple = new CApiBuiltinExecutable("PyList_AsTuple", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 86);
    public static final CApiBuiltinExecutable PyList_GetItem = new CApiBuiltinExecutable("PyList_GetItem", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 87);
    public static final CApiBuiltinExecutable PyList_GetSlice = new CApiBuiltinExecutable("PyList_GetSlice", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t}, 88);
    public static final CApiBuiltinExecutable PyList_Insert = new CApiBuiltinExecutable("PyList_Insert", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.PyObject}, 89);
    public static final CApiBuiltinExecutable PyList_New = new CApiBuiltinExecutable("PyList_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Py_ssize_t}, 90);
    public static final CApiBuiltinExecutable PyList_Reverse = new CApiBuiltinExecutable("PyList_Reverse", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 91);
    public static final CApiBuiltinExecutable PyList_SetItem = new CApiBuiltinExecutable("PyList_SetItem", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.PyObjectTransfer}, 92);
    public static final CApiBuiltinExecutable PyList_SetSlice = new CApiBuiltinExecutable("PyList_SetSlice", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t, ArgDescriptor.PyObject}, 93);
    public static final CApiBuiltinExecutable PyList_Size = new CApiBuiltinExecutable("PyList_Size", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 94);
    public static final CApiBuiltinExecutable PyList_Sort = new CApiBuiltinExecutable("PyList_Sort", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 95);
    public static final CApiBuiltinExecutable PyLong_AsVoidPtr = new CApiBuiltinExecutable("PyLong_AsVoidPtr", CApiCallPath.Direct, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 96);
    public static final CApiBuiltinExecutable PyLong_FromDouble = new CApiBuiltinExecutable("PyLong_FromDouble", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Double}, 97);
    public static final CApiBuiltinExecutable PyLong_FromLong = new CApiBuiltinExecutable("PyLong_FromLong", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Long}, 98);
    public static final CApiBuiltinExecutable PyLong_FromLongLong = new CApiBuiltinExecutable("PyLong_FromLongLong", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.LONG_LONG}, 99);
    public static final CApiBuiltinExecutable PyLong_FromSize_t = new CApiBuiltinExecutable("PyLong_FromSize_t", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.SIZE_T}, 100);
    public static final CApiBuiltinExecutable PyLong_FromSsize_t = new CApiBuiltinExecutable("PyLong_FromSsize_t", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Py_ssize_t}, 101);
    public static final CApiBuiltinExecutable PyLong_FromUnsignedLong = new CApiBuiltinExecutable("PyLong_FromUnsignedLong", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.UNSIGNED_LONG}, 102);
    public static final CApiBuiltinExecutable PyLong_FromUnsignedLongLong = new CApiBuiltinExecutable("PyLong_FromUnsignedLongLong", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.UNSIGNED_LONG_LONG}, 103);
    public static final CApiBuiltinExecutable PyMapping_Check = new CApiBuiltinExecutable("PyMapping_Check", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 104);
    public static final CApiBuiltinExecutable PyMapping_Items = new CApiBuiltinExecutable("PyMapping_Items", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 105);
    public static final CApiBuiltinExecutable PyMapping_Keys = new CApiBuiltinExecutable("PyMapping_Keys", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 106);
    public static final CApiBuiltinExecutable PyMapping_Size = new CApiBuiltinExecutable("PyMapping_Size", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 107);
    public static final CApiBuiltinExecutable PyMapping_Values = new CApiBuiltinExecutable("PyMapping_Values", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 108);
    public static final CApiBuiltinExecutable PyMemoryView_FromObject = new CApiBuiltinExecutable("PyMemoryView_FromObject", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 109);
    public static final CApiBuiltinExecutable PyMemoryView_GetContiguous = new CApiBuiltinExecutable("PyMemoryView_GetContiguous", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Int, ArgDescriptor.CHAR}, 110);
    public static final CApiBuiltinExecutable PyMethod_New = new CApiBuiltinExecutable("PyMethod_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 111);
    public static final CApiBuiltinExecutable PyModule_AddIntConstant = new CApiBuiltinExecutable("PyModule_AddIntConstant", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Long}, 112);
    public static final CApiBuiltinExecutable PyModule_AddObjectRef = new CApiBuiltinExecutable("PyModule_AddObjectRef", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PyObject}, 113);
    public static final CApiBuiltinExecutable PyModule_GetNameObject = new CApiBuiltinExecutable("PyModule_GetNameObject", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 114);
    public static final CApiBuiltinExecutable PyModule_New = new CApiBuiltinExecutable("PyModule_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 115);
    public static final CApiBuiltinExecutable PyModule_NewObject = new CApiBuiltinExecutable("PyModule_NewObject", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObjectAsTruffleString}, 116);
    public static final CApiBuiltinExecutable PyModule_SetDocString = new CApiBuiltinExecutable("PyModule_SetDocString", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 117);
    public static final CApiBuiltinExecutable PyNumber_Absolute = new CApiBuiltinExecutable("PyNumber_Absolute", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 118);
    public static final CApiBuiltinExecutable PyNumber_Check = new CApiBuiltinExecutable("PyNumber_Check", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 119);
    public static final CApiBuiltinExecutable PyNumber_Divmod = new CApiBuiltinExecutable("PyNumber_Divmod", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 120);
    public static final CApiBuiltinExecutable PyNumber_Float = new CApiBuiltinExecutable("PyNumber_Float", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 121);
    public static final CApiBuiltinExecutable PyNumber_InPlacePower = new CApiBuiltinExecutable("PyNumber_InPlacePower", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 122);
    public static final CApiBuiltinExecutable PyNumber_Index = new CApiBuiltinExecutable("PyNumber_Index", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 123);
    public static final CApiBuiltinExecutable PyNumber_Long = new CApiBuiltinExecutable("PyNumber_Long", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 124);
    public static final CApiBuiltinExecutable PyNumber_Power = new CApiBuiltinExecutable("PyNumber_Power", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 125);
    public static final CApiBuiltinExecutable PyNumber_ToBase = new CApiBuiltinExecutable("PyNumber_ToBase", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Int}, 126);
    public static final CApiBuiltinExecutable PyOS_FSPath = new CApiBuiltinExecutable("PyOS_FSPath", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 127);
    public static final CApiBuiltinExecutable PyObject_ASCII = new CApiBuiltinExecutable("PyObject_ASCII", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 128);
    public static final CApiBuiltinExecutable PyObject_AsFileDescriptor = new CApiBuiltinExecutable("PyObject_AsFileDescriptor", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 129);
    public static final CApiBuiltinExecutable PyObject_Bytes = new CApiBuiltinExecutable("PyObject_Bytes", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 130);
    public static final CApiBuiltinExecutable PyObject_ClearWeakRefs = new CApiBuiltinExecutable("PyObject_ClearWeakRefs", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObject}, 131);
    public static final CApiBuiltinExecutable PyObject_DelItem = new CApiBuiltinExecutable("PyObject_DelItem", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 132);
    public static final CApiBuiltinExecutable PyObject_Dir = new CApiBuiltinExecutable("PyObject_Dir", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 133);
    public static final CApiBuiltinExecutable PyObject_Format = new CApiBuiltinExecutable("PyObject_Format", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 134);
    public static final CApiBuiltinExecutable PyObject_GC_Track = new CApiBuiltinExecutable("PyObject_GC_Track", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.Pointer}, 135);
    public static final CApiBuiltinExecutable PyObject_GC_UnTrack = new CApiBuiltinExecutable("PyObject_GC_UnTrack", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.Pointer}, 136);
    public static final CApiBuiltinExecutable PyObject_GetDoc = new CApiBuiltinExecutable("PyObject_GetDoc", CApiCallPath.Direct, ArgDescriptor.ConstCharPtr, new ArgDescriptor[]{ArgDescriptor.PyObject}, 137);
    public static final CApiBuiltinExecutable PyObject_GetItem = new CApiBuiltinExecutable("PyObject_GetItem", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 138);
    public static final CApiBuiltinExecutable PyObject_GetIter = new CApiBuiltinExecutable("PyObject_GetIter", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 139);
    public static final CApiBuiltinExecutable PyObject_HasAttr = new CApiBuiltinExecutable("PyObject_HasAttr", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 140);
    public static final CApiBuiltinExecutable PyObject_HasAttrString = new CApiBuiltinExecutable("PyObject_HasAttrString", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 141);
    public static final CApiBuiltinExecutable PyObject_Hash = new CApiBuiltinExecutable("PyObject_Hash", CApiCallPath.Direct, ArgDescriptor.Py_hash_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 142);
    public static final CApiBuiltinExecutable PyObject_HashNotImplemented = new CApiBuiltinExecutable("PyObject_HashNotImplemented", CApiCallPath.Direct, ArgDescriptor.Py_hash_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 143);
    public static final CApiBuiltinExecutable PyObject_IsInstance = new CApiBuiltinExecutable("PyObject_IsInstance", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 144);
    public static final CApiBuiltinExecutable PyObject_IsSubclass = new CApiBuiltinExecutable("PyObject_IsSubclass", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 145);
    public static final CApiBuiltinExecutable PyObject_IsTrue = new CApiBuiltinExecutable("PyObject_IsTrue", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 146);
    public static final CApiBuiltinExecutable PyObject_LengthHint = new CApiBuiltinExecutable("PyObject_LengthHint", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 147);
    public static final CApiBuiltinExecutable PyObject_Repr = new CApiBuiltinExecutable("PyObject_Repr", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 148);
    public static final CApiBuiltinExecutable PyObject_RichCompare = new CApiBuiltinExecutable("PyObject_RichCompare", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Int}, 149);
    public static final CApiBuiltinExecutable PyObject_SetDoc = new CApiBuiltinExecutable("PyObject_SetDoc", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 150);
    public static final CApiBuiltinExecutable PyObject_SetItem = new CApiBuiltinExecutable("PyObject_SetItem", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 151);
    public static final CApiBuiltinExecutable PyObject_Size = new CApiBuiltinExecutable("PyObject_Size", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 152);
    public static final CApiBuiltinExecutable PyObject_Str = new CApiBuiltinExecutable("PyObject_Str", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 153);
    public static final CApiBuiltinExecutable PyObject_Type = new CApiBuiltinExecutable("PyObject_Type", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 154);
    public static final CApiBuiltinExecutable PyRun_StringFlags = new CApiBuiltinExecutable("PyRun_StringFlags", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PY_COMPILER_FLAGS}, 155);
    public static final CApiBuiltinExecutable PySeqIter_New = new CApiBuiltinExecutable("PySeqIter_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 156);
    public static final CApiBuiltinExecutable PySequence_Check = new CApiBuiltinExecutable("PySequence_Check", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 157);
    public static final CApiBuiltinExecutable PySequence_Concat = new CApiBuiltinExecutable("PySequence_Concat", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 158);
    public static final CApiBuiltinExecutable PySequence_Contains = new CApiBuiltinExecutable("PySequence_Contains", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 159);
    public static final CApiBuiltinExecutable PySequence_Count = new CApiBuiltinExecutable("PySequence_Count", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 160);
    public static final CApiBuiltinExecutable PySequence_DelItem = new CApiBuiltinExecutable("PySequence_DelItem", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 161);
    public static final CApiBuiltinExecutable PySequence_DelSlice = new CApiBuiltinExecutable("PySequence_DelSlice", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t}, 162);
    public static final CApiBuiltinExecutable PySequence_GetItem = new CApiBuiltinExecutable("PySequence_GetItem", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 163);
    public static final CApiBuiltinExecutable PySequence_GetSlice = new CApiBuiltinExecutable("PySequence_GetSlice", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t}, 164);
    public static final CApiBuiltinExecutable PySequence_InPlaceConcat = new CApiBuiltinExecutable("PySequence_InPlaceConcat", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 165);
    public static final CApiBuiltinExecutable PySequence_InPlaceRepeat = new CApiBuiltinExecutable("PySequence_InPlaceRepeat", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 166);
    public static final CApiBuiltinExecutable PySequence_Index = new CApiBuiltinExecutable("PySequence_Index", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 167);
    public static final CApiBuiltinExecutable PySequence_Length = new CApiBuiltinExecutable("PySequence_Length", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 168);
    public static final CApiBuiltinExecutable PySequence_List = new CApiBuiltinExecutable("PySequence_List", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 169);
    public static final CApiBuiltinExecutable PySequence_Repeat = new CApiBuiltinExecutable("PySequence_Repeat", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 170);
    public static final CApiBuiltinExecutable PySequence_SetItem = new CApiBuiltinExecutable("PySequence_SetItem", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.PyObject}, 171);
    public static final CApiBuiltinExecutable PySequence_SetSlice = new CApiBuiltinExecutable("PySequence_SetSlice", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t, ArgDescriptor.PyObject}, 172);
    public static final CApiBuiltinExecutable PySequence_Size = new CApiBuiltinExecutable("PySequence_Size", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 173);
    public static final CApiBuiltinExecutable PySequence_Tuple = new CApiBuiltinExecutable("PySequence_Tuple", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 174);
    public static final CApiBuiltinExecutable PySet_Add = new CApiBuiltinExecutable("PySet_Add", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 175);
    public static final CApiBuiltinExecutable PySet_Clear = new CApiBuiltinExecutable("PySet_Clear", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 176);
    public static final CApiBuiltinExecutable PySet_Contains = new CApiBuiltinExecutable("PySet_Contains", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 177);
    public static final CApiBuiltinExecutable PySet_Discard = new CApiBuiltinExecutable("PySet_Discard", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 178);
    public static final CApiBuiltinExecutable PySet_New = new CApiBuiltinExecutable("PySet_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 179);
    public static final CApiBuiltinExecutable PySet_Pop = new CApiBuiltinExecutable("PySet_Pop", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 180);
    public static final CApiBuiltinExecutable PySet_Size = new CApiBuiltinExecutable("PySet_Size", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 181);
    public static final CApiBuiltinExecutable PySlice_New = new CApiBuiltinExecutable("PySlice_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 182);
    public static final CApiBuiltinExecutable PyStaticMethod_New = new CApiBuiltinExecutable("PyStaticMethod_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 183);
    public static final CApiBuiltinExecutable PyStructSequence_New = new CApiBuiltinExecutable("PyStructSequence_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 184);
    public static final CApiBuiltinExecutable PySys_GetObject = new CApiBuiltinExecutable("PySys_GetObject", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 185);
    public static final CApiBuiltinExecutable PyThreadState_Get = new CApiBuiltinExecutable("PyThreadState_Get", CApiCallPath.Direct, ArgDescriptor.PyThreadState, new ArgDescriptor[]{}, 186);
    public static final CApiBuiltinExecutable PyThreadState_GetDict = new CApiBuiltinExecutable("PyThreadState_GetDict", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{}, 187);
    public static final CApiBuiltinExecutable PyThread_acquire_lock = new CApiBuiltinExecutable("PyThread_acquire_lock", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PY_THREAD_TYPE_LOCK, ArgDescriptor.Int}, 188);
    public static final CApiBuiltinExecutable PyThread_allocate_lock = new CApiBuiltinExecutable("PyThread_allocate_lock", CApiCallPath.Direct, ArgDescriptor.PY_THREAD_TYPE_LOCK, new ArgDescriptor[]{}, 189);
    public static final CApiBuiltinExecutable PyThread_get_thread_ident = new CApiBuiltinExecutable("PyThread_get_thread_ident", CApiCallPath.Direct, ArgDescriptor.UNSIGNED_LONG, new ArgDescriptor[]{}, 190);
    public static final CApiBuiltinExecutable PyThread_release_lock = new CApiBuiltinExecutable("PyThread_release_lock", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PY_THREAD_TYPE_LOCK}, 191);
    public static final CApiBuiltinExecutable PyTraceBack_Here = new CApiBuiltinExecutable("PyTraceBack_Here", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyFrameObject}, 192);
    public static final CApiBuiltinExecutable PyTraceMalloc_Track = new CApiBuiltinExecutable("PyTraceMalloc_Track", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.UNSIGNED_INT, ArgDescriptor.UINTPTR_T, ArgDescriptor.SIZE_T}, 193);
    public static final CApiBuiltinExecutable PyTraceMalloc_Untrack = new CApiBuiltinExecutable("PyTraceMalloc_Untrack", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.UNSIGNED_INT, ArgDescriptor.UINTPTR_T}, 194);
    public static final CApiBuiltinExecutable PyTruffleByteArray_FromStringAndSize = new CApiBuiltinExecutable("PyTruffleByteArray_FromStringAndSize", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.INT8_T_PTR, ArgDescriptor.Py_ssize_t}, 195);
    public static final CApiBuiltinExecutable PyTruffleBytes_Concat = new CApiBuiltinExecutable("PyTruffleBytes_Concat", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 196);
    public static final CApiBuiltinExecutable PyTruffleBytes_FromFormat = new CApiBuiltinExecutable("PyTruffleBytes_FromFormat", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PyObject}, 197);
    public static final CApiBuiltinExecutable PyTruffleBytes_FromStringAndSize = new CApiBuiltinExecutable("PyTruffleBytes_FromStringAndSize", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtr, ArgDescriptor.Py_ssize_t}, 198);
    public static final CApiBuiltinExecutable PyTruffleCMethod_NewEx = new CApiBuiltinExecutable("PyTruffleCMethod_NewEx", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyMethodDef, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Pointer, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyTypeObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 199);
    public static final CApiBuiltinExecutable PyTruffleComplex_AsCComplex = new CApiBuiltinExecutable("PyTruffleComplex_AsCComplex", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 200);
    public static final CApiBuiltinExecutable PyTruffleContextVar_Get = new CApiBuiltinExecutable("PyTruffleContextVar_Get", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Pointer}, 201);
    public static final CApiBuiltinExecutable PyTruffleDateTimeCAPI_DateTime_FromDateAndTime = new CApiBuiltinExecutable("PyTruffleDateTimeCAPI_DateTime_FromDateAndTime", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.PyObject, ArgDescriptor.PyTypeObject}, 202);
    public static final CApiBuiltinExecutable PyTruffleDateTimeCAPI_DateTime_FromDateAndTimeAndFold = new CApiBuiltinExecutable("PyTruffleDateTimeCAPI_DateTime_FromDateAndTimeAndFold", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.PyObject, ArgDescriptor.Int, ArgDescriptor.PyTypeObject}, 203);
    public static final CApiBuiltinExecutable PyTruffleDateTimeCAPI_DateTime_FromTimestamp = new CApiBuiltinExecutable("PyTruffleDateTimeCAPI_DateTime_FromTimestamp", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 204);
    public static final CApiBuiltinExecutable PyTruffleDateTimeCAPI_Date_FromDate = new CApiBuiltinExecutable("PyTruffleDateTimeCAPI_Date_FromDate", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.PyTypeObject}, 205);
    public static final CApiBuiltinExecutable PyTruffleDateTimeCAPI_Date_FromTimestamp = new CApiBuiltinExecutable("PyTruffleDateTimeCAPI_Date_FromTimestamp", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 206);
    public static final CApiBuiltinExecutable PyTruffleDateTimeCAPI_Delta_FromDelta = new CApiBuiltinExecutable("PyTruffleDateTimeCAPI_Delta_FromDelta", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.PyTypeObject}, 207);
    public static final CApiBuiltinExecutable PyTruffleDateTimeCAPI_TimeZone_FromTimeZone = new CApiBuiltinExecutable("PyTruffleDateTimeCAPI_TimeZone_FromTimeZone", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 208);
    public static final CApiBuiltinExecutable PyTruffleDateTimeCAPI_Time_FromTime = new CApiBuiltinExecutable("PyTruffleDateTimeCAPI_Time_FromTime", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.PyObject, ArgDescriptor.PyTypeObject}, 209);
    public static final CApiBuiltinExecutable PyTruffleDateTimeCAPI_Time_FromTimeAndFold = new CApiBuiltinExecutable("PyTruffleDateTimeCAPI_Time_FromTimeAndFold", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.PyObject, ArgDescriptor.Int, ArgDescriptor.PyTypeObject}, 210);
    public static final CApiBuiltinExecutable PyTruffleDescr_NewClassMethod = new CApiBuiltinExecutable("PyTruffleDescr_NewClassMethod", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.Pointer, ArgDescriptor.PyTypeObject}, 211);
    public static final CApiBuiltinExecutable PyTruffleDescr_NewGetSet = new CApiBuiltinExecutable("PyTruffleDescr_NewGetSet", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PyTypeObject, ArgDescriptor.Pointer, ArgDescriptor.Pointer, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Pointer}, 212);
    public static final CApiBuiltinExecutable PyTruffleDict_Next = new CApiBuiltinExecutable("PyTruffleDict_Next", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 213);
    public static final CApiBuiltinExecutable PyTruffleErr_Fetch = new CApiBuiltinExecutable("PyTruffleErr_Fetch", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 214);
    public static final CApiBuiltinExecutable PyTruffleErr_GetExcInfo = new CApiBuiltinExecutable("PyTruffleErr_GetExcInfo", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 215);
    public static final CApiBuiltinExecutable PyTruffleErr_WarnExplicit = new CApiBuiltinExecutable("PyTruffleErr_WarnExplicit", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Int, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 216);
    public static final CApiBuiltinExecutable PyTruffleFloat_AsDouble = new CApiBuiltinExecutable("PyTruffleFloat_AsDouble", CApiCallPath.Ignored, ArgDescriptor.Double, new ArgDescriptor[]{ArgDescriptor.PyObject}, 217);
    public static final CApiBuiltinExecutable PyTruffleGILState_Ensure = new CApiBuiltinExecutable("PyTruffleGILState_Ensure", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{}, 218);
    public static final CApiBuiltinExecutable PyTruffleGILState_Release = new CApiBuiltinExecutable("PyTruffleGILState_Release", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{}, 219);
    public static final CApiBuiltinExecutable PyTruffleHash_InitSecret = new CApiBuiltinExecutable("PyTruffleHash_InitSecret", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.Pointer}, 220);
    public static final CApiBuiltinExecutable PyTruffleLong_AsPrimitive = new CApiBuiltinExecutable("PyTruffleLong_AsPrimitive", CApiCallPath.Ignored, ArgDescriptor.Long, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Int, ArgDescriptor.Long}, 221);
    public static final CApiBuiltinExecutable PyTruffleLong_FromString = new CApiBuiltinExecutable("PyTruffleLong_FromString", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int, ArgDescriptor.Int}, 222);
    public static final CApiBuiltinExecutable PyTruffleLong_One = new CApiBuiltinExecutable("PyTruffleLong_One", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 223);
    public static final CApiBuiltinExecutable PyTruffleLong_Zero = new CApiBuiltinExecutable("PyTruffleLong_Zero", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 224);
    public static final CApiBuiltinExecutable PyTruffleModule_AddFunctionToModule = new CApiBuiltinExecutable("PyTruffleModule_AddFunctionToModule", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Pointer, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.ConstCharPtrAsTruffleString}, 225);
    public static final CApiBuiltinExecutable PyTruffleNumber_BinOp = new CApiBuiltinExecutable("PyTruffleNumber_BinOp", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Int}, 226);
    public static final CApiBuiltinExecutable PyTruffleNumber_InPlaceBinOp = new CApiBuiltinExecutable("PyTruffleNumber_InPlaceBinOp", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Int}, 227);
    public static final CApiBuiltinExecutable PyTruffleNumber_UnaryOp = new CApiBuiltinExecutable("PyTruffleNumber_UnaryOp", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Int}, 228);
    public static final CApiBuiltinExecutable PyTruffleObject_CallFunctionObjArgs = new CApiBuiltinExecutable("PyTruffleObject_CallFunctionObjArgs", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.VA_LIST_PTR}, 229);
    public static final CApiBuiltinExecutable PyTruffleObject_CallMethodObjArgs = new CApiBuiltinExecutable("PyTruffleObject_CallMethodObjArgs", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.VA_LIST_PTR}, 230);
    public static final CApiBuiltinExecutable PyTruffleObject_GenericGetAttr = new CApiBuiltinExecutable("PyTruffleObject_GenericGetAttr", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 231);
    public static final CApiBuiltinExecutable PyTruffleObject_GenericSetAttr = new CApiBuiltinExecutable("PyTruffleObject_GenericSetAttr", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 232);
    public static final CApiBuiltinExecutable PyTruffleObject_GetItemString = new CApiBuiltinExecutable("PyTruffleObject_GetItemString", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 233);
    public static final CApiBuiltinExecutable PyTruffleState_FindModule = new CApiBuiltinExecutable("PyTruffleState_FindModule", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.Py_ssize_t}, 234);
    public static final CApiBuiltinExecutable PyTruffleStructSequence_InitType2 = new CApiBuiltinExecutable("PyTruffleStructSequence_InitType2", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.Pointer, ArgDescriptor.Pointer, ArgDescriptor.Int}, 235);
    public static final CApiBuiltinExecutable PyTruffleStructSequence_NewType = new CApiBuiltinExecutable("PyTruffleStructSequence_NewType", CApiCallPath.Ignored, ArgDescriptor.PyTypeObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Pointer, ArgDescriptor.Pointer, ArgDescriptor.Int}, 236);
    public static final CApiBuiltinExecutable PyTruffleToCharPointer = new CApiBuiltinExecutable("PyTruffleToCharPointer", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 237);
    public static final CApiBuiltinExecutable PyTruffleType_AddFunctionToType = new CApiBuiltinExecutable("PyTruffleType_AddFunctionToType", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.PyTypeObject, ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Pointer, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.ConstCharPtrAsTruffleString}, 238);
    public static final CApiBuiltinExecutable PyTruffleType_AddGetSet = new CApiBuiltinExecutable("PyTruffleType_AddGetSet", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Pointer, ArgDescriptor.Pointer, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Pointer}, 239);
    public static final CApiBuiltinExecutable PyTruffleType_AddMember = new CApiBuiltinExecutable("PyTruffleType_AddMember", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int, ArgDescriptor.Py_ssize_t, ArgDescriptor.Int, ArgDescriptor.ConstCharPtrAsTruffleString}, 240);
    public static final CApiBuiltinExecutable PyTruffleType_AddSlot = new CApiBuiltinExecutable("PyTruffleType_AddSlot", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Pointer, ArgDescriptor.Int, ArgDescriptor.Int, ArgDescriptor.ConstCharPtrAsTruffleString}, 241);
    public static final CApiBuiltinExecutable PyTruffleUnicode_Decode = new CApiBuiltinExecutable("PyTruffleUnicode_Decode", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString}, 242);
    public static final CApiBuiltinExecutable PyTruffleUnicode_DecodeUTF8Stateful = new CApiBuiltinExecutable("PyTruffleUnicode_DecodeUTF8Stateful", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int}, 243);
    public static final CApiBuiltinExecutable PyTruffleUnicode_FromUCS = new CApiBuiltinExecutable("PyTruffleUnicode_FromUCS", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.Py_ssize_t, ArgDescriptor.Int}, 244);
    public static final CApiBuiltinExecutable PyTruffleUnicode_InternInPlace = new CApiBuiltinExecutable("PyTruffleUnicode_InternInPlace", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyObject}, 245);
    public static final CApiBuiltinExecutable PyTruffleUnicode_New = new CApiBuiltinExecutable("PyTruffleUnicode_New", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.Py_ssize_t, ArgDescriptor.PY_UCS4}, 246);
    public static final CApiBuiltinExecutable PyTruffle_Arg_ParseTupleAndKeywords = new CApiBuiltinExecutable("PyTruffle_Arg_ParseTupleAndKeywords", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Pointer, ArgDescriptor.Pointer}, 247);
    public static final CApiBuiltinExecutable PyTruffle_ByteArray_EmptyWithCapacity = new CApiBuiltinExecutable("PyTruffle_ByteArray_EmptyWithCapacity", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Py_ssize_t}, 248);
    public static final CApiBuiltinExecutable PyTruffle_Bytes_AsString = new CApiBuiltinExecutable("PyTruffle_Bytes_AsString", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 249);
    public static final CApiBuiltinExecutable PyTruffle_Bytes_CheckEmbeddedNull = new CApiBuiltinExecutable("PyTruffle_Bytes_CheckEmbeddedNull", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 250);
    public static final CApiBuiltinExecutable PyTruffle_Bytes_EmptyWithCapacity = new CApiBuiltinExecutable("PyTruffle_Bytes_EmptyWithCapacity", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Long}, 251);
    public static final CApiBuiltinExecutable PyTruffle_Compute_Mro = new CApiBuiltinExecutable("PyTruffle_Compute_Mro", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 252);
    public static final CApiBuiltinExecutable PyTruffle_Debug = new CApiBuiltinExecutable("PyTruffle_Debug", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.Pointer}, 253);
    public static final CApiBuiltinExecutable PyTruffle_DebugTrace = new CApiBuiltinExecutable("PyTruffle_DebugTrace", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{}, 254);
    public static final CApiBuiltinExecutable PyTruffle_Ellipsis = new CApiBuiltinExecutable("PyTruffle_Ellipsis", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 255);
    public static final CApiBuiltinExecutable PyTruffle_False = new CApiBuiltinExecutable("PyTruffle_False", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 256);
    public static final CApiBuiltinExecutable PyTruffle_FatalErrorFunc = new CApiBuiltinExecutable("PyTruffle_FatalErrorFunc", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int}, 257);
    public static final CApiBuiltinExecutable PyTruffle_FileSystemDefaultEncoding = new CApiBuiltinExecutable("PyTruffle_FileSystemDefaultEncoding", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 258);
    public static final CApiBuiltinExecutable PyTruffle_Get_Inherited_Native_Slots = new CApiBuiltinExecutable("PyTruffle_Get_Inherited_Native_Slots", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 259);
    public static final CApiBuiltinExecutable PyTruffle_HashConstant = new CApiBuiltinExecutable("PyTruffle_HashConstant", CApiCallPath.Ignored, ArgDescriptor.Long, new ArgDescriptor[]{ArgDescriptor.Int}, 260);
    public static final CApiBuiltinExecutable PyTruffle_InitialNativeMemory = new CApiBuiltinExecutable("PyTruffle_InitialNativeMemory", CApiCallPath.Ignored, ArgDescriptor.SIZE_T, new ArgDescriptor[]{}, 261);
    public static final CApiBuiltinExecutable PyTruffle_LogString = new CApiBuiltinExecutable("PyTruffle_LogString", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.Int, ArgDescriptor.ConstCharPtrAsTruffleString}, 262);
    public static final CApiBuiltinExecutable PyTruffle_MaxNativeMemory = new CApiBuiltinExecutable("PyTruffle_MaxNativeMemory", CApiCallPath.Ignored, ArgDescriptor.SIZE_T, new ArgDescriptor[]{}, 263);
    public static final CApiBuiltinExecutable PyTruffle_MemoryViewFromBuffer = new CApiBuiltinExecutable("PyTruffle_MemoryViewFromBuffer", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.Int, ArgDescriptor.Py_ssize_t, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int, ArgDescriptor.Pointer, ArgDescriptor.Pointer, ArgDescriptor.Pointer, ArgDescriptor.Pointer}, 264);
    public static final CApiBuiltinExecutable PyTruffle_Native_Options = new CApiBuiltinExecutable("PyTruffle_Native_Options", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{}, 265);
    public static final CApiBuiltinExecutable PyTruffle_NewTypeDict = new CApiBuiltinExecutable("PyTruffle_NewTypeDict", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 266);
    public static final CApiBuiltinExecutable PyTruffle_NoValue = new CApiBuiltinExecutable("PyTruffle_NoValue", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 267);
    public static final CApiBuiltinExecutable PyTruffle_None = new CApiBuiltinExecutable("PyTruffle_None", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 268);
    public static final CApiBuiltinExecutable PyTruffle_NotImplemented = new CApiBuiltinExecutable("PyTruffle_NotImplemented", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 269);
    public static final CApiBuiltinExecutable PyTruffle_Object_Free = new CApiBuiltinExecutable("PyTruffle_Object_Free", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.Pointer}, 270);
    public static final CApiBuiltinExecutable PyTruffle_PyDateTime_GET_TZINFO = new CApiBuiltinExecutable("PyTruffle_PyDateTime_GET_TZINFO", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyObject}, 271);
    public static final CApiBuiltinExecutable PyTruffle_Register_NULL = new CApiBuiltinExecutable("PyTruffle_Register_NULL", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.Pointer}, 272);
    public static final CApiBuiltinExecutable PyTruffle_Set_Native_Slots = new CApiBuiltinExecutable("PyTruffle_Set_Native_Slots", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.Pointer, ArgDescriptor.Pointer}, 273);
    public static final CApiBuiltinExecutable PyTruffle_Set_SulongType = new CApiBuiltinExecutable("PyTruffle_Set_SulongType", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.Pointer}, 274);
    public static final CApiBuiltinExecutable PyTruffle_ToNative = new CApiBuiltinExecutable("PyTruffle_ToNative", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.Pointer}, 275);
    public static final CApiBuiltinExecutable PyTruffle_Trace_Type = new CApiBuiltinExecutable("PyTruffle_Trace_Type", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.Pointer}, 276);
    public static final CApiBuiltinExecutable PyTruffle_TriggerGC = new CApiBuiltinExecutable("PyTruffle_TriggerGC", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.SIZE_T}, 277);
    public static final CApiBuiltinExecutable PyTruffle_True = new CApiBuiltinExecutable("PyTruffle_True", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{}, 278);
    public static final CApiBuiltinExecutable PyTruffle_Type = new CApiBuiltinExecutable("PyTruffle_Type", CApiCallPath.Ignored, ArgDescriptor.PyTypeObject, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 279);
    public static final CApiBuiltinExecutable PyTruffle_Type_Modified = new CApiBuiltinExecutable("PyTruffle_Type_Modified", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PyObject}, 280);
    public static final CApiBuiltinExecutable PyTruffle_Unicode_AsUTF8AndSize_CharPtr = new CApiBuiltinExecutable("PyTruffle_Unicode_AsUTF8AndSize_CharPtr", CApiCallPath.Direct, ArgDescriptor.ConstCharPtr, new ArgDescriptor[]{ArgDescriptor.PyObject}, 281);
    public static final CApiBuiltinExecutable PyTruffle_Unicode_AsUTF8AndSize_Size = new CApiBuiltinExecutable("PyTruffle_Unicode_AsUTF8AndSize_Size", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 282);
    public static final CApiBuiltinExecutable PyTruffle_Unicode_AsUnicodeAndSize_CharPtr = new CApiBuiltinExecutable("PyTruffle_Unicode_AsUnicodeAndSize_CharPtr", CApiCallPath.Direct, ArgDescriptor.PY_UNICODE_PTR, new ArgDescriptor[]{ArgDescriptor.PyObject}, 283);
    public static final CApiBuiltinExecutable PyTruffle_Unicode_AsUnicodeAndSize_Size = new CApiBuiltinExecutable("PyTruffle_Unicode_AsUnicodeAndSize_Size", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 284);
    public static final CApiBuiltinExecutable PyTruffle_Unicode_AsWideChar = new CApiBuiltinExecutable("PyTruffle_Unicode_AsWideChar", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Int}, 285);
    public static final CApiBuiltinExecutable PyTruffle_Unicode_DecodeUTF32 = new CApiBuiltinExecutable("PyTruffle_Unicode_DecodeUTF32", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.Py_ssize_t, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int}, 286);
    public static final CApiBuiltinExecutable PyTruffle_Unicode_FromFormat = new CApiBuiltinExecutable("PyTruffle_Unicode_FromFormat", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.VA_LIST_PTR}, 287);
    public static final CApiBuiltinExecutable PyTruffle_Unicode_FromWchar = new CApiBuiltinExecutable("PyTruffle_Unicode_FromWchar", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Pointer, ArgDescriptor.SIZE_T}, 288);
    public static final CApiBuiltinExecutable PyTruffle_tss_create = new CApiBuiltinExecutable("PyTruffle_tss_create", CApiCallPath.Ignored, ArgDescriptor.Long, new ArgDescriptor[]{}, 289);
    public static final CApiBuiltinExecutable PyTruffle_tss_delete = new CApiBuiltinExecutable("PyTruffle_tss_delete", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.Long}, 290);
    public static final CApiBuiltinExecutable PyTruffle_tss_get = new CApiBuiltinExecutable("PyTruffle_tss_get", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.Long}, 291);
    public static final CApiBuiltinExecutable PyTruffle_tss_set = new CApiBuiltinExecutable("PyTruffle_tss_set", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.Long, ArgDescriptor.Pointer}, 292);
    public static final CApiBuiltinExecutable PyTuple_GetItem = new CApiBuiltinExecutable("PyTuple_GetItem", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 293);
    public static final CApiBuiltinExecutable PyTuple_GetSlice = new CApiBuiltinExecutable("PyTuple_GetSlice", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t}, 294);
    public static final CApiBuiltinExecutable PyTuple_New = new CApiBuiltinExecutable("PyTuple_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Py_ssize_t}, 295);
    public static final CApiBuiltinExecutable PyTuple_SetItem = new CApiBuiltinExecutable("PyTuple_SetItem", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.PyObjectTransfer}, 296);
    public static final CApiBuiltinExecutable PyTuple_Size = new CApiBuiltinExecutable("PyTuple_Size", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject}, 297);
    public static final CApiBuiltinExecutable PyType_IsSubtype = new CApiBuiltinExecutable("PyType_IsSubtype", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyTypeObject}, 298);
    public static final CApiBuiltinExecutable PyUnicodeDecodeError_Create = new CApiBuiltinExecutable("PyUnicodeDecodeError_Create", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtr, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t, ArgDescriptor.ConstCharPtrAsTruffleString}, 299);
    public static final CApiBuiltinExecutable PyUnicode_AsEncodedString = new CApiBuiltinExecutable("PyUnicode_AsEncodedString", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString}, 300);
    public static final CApiBuiltinExecutable PyUnicode_AsUnicodeEscapeString = new CApiBuiltinExecutable("PyUnicode_AsUnicodeEscapeString", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 301);
    public static final CApiBuiltinExecutable PyUnicode_Compare = new CApiBuiltinExecutable("PyUnicode_Compare", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 302);
    public static final CApiBuiltinExecutable PyUnicode_Concat = new CApiBuiltinExecutable("PyUnicode_Concat", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 303);
    public static final CApiBuiltinExecutable PyUnicode_Contains = new CApiBuiltinExecutable("PyUnicode_Contains", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 304);
    public static final CApiBuiltinExecutable PyUnicode_DecodeFSDefault = new CApiBuiltinExecutable("PyUnicode_DecodeFSDefault", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 305);
    public static final CApiBuiltinExecutable PyUnicode_EncodeFSDefault = new CApiBuiltinExecutable("PyUnicode_EncodeFSDefault", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 306);
    public static final CApiBuiltinExecutable PyUnicode_FindChar = new CApiBuiltinExecutable("PyUnicode_FindChar", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PY_UCS4, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t, ArgDescriptor.Int}, 307);
    public static final CApiBuiltinExecutable PyUnicode_Format = new CApiBuiltinExecutable("PyUnicode_Format", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 308);
    public static final CApiBuiltinExecutable PyUnicode_FromEncodedObject = new CApiBuiltinExecutable("PyUnicode_FromEncodedObject", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString}, 309);
    public static final CApiBuiltinExecutable PyUnicode_FromObject = new CApiBuiltinExecutable("PyUnicode_FromObject", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 310);
    public static final CApiBuiltinExecutable PyUnicode_FromOrdinal = new CApiBuiltinExecutable("PyUnicode_FromOrdinal", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.Int}, 311);
    public static final CApiBuiltinExecutable PyUnicode_FromString = new CApiBuiltinExecutable("PyUnicode_FromString", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 312);
    public static final CApiBuiltinExecutable PyUnicode_Join = new CApiBuiltinExecutable("PyUnicode_Join", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 313);
    public static final CApiBuiltinExecutable PyUnicode_ReadChar = new CApiBuiltinExecutable("PyUnicode_ReadChar", CApiCallPath.Direct, ArgDescriptor.PY_UCS4, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 314);
    public static final CApiBuiltinExecutable PyUnicode_Replace = new CApiBuiltinExecutable("PyUnicode_Replace", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 315);
    public static final CApiBuiltinExecutable PyUnicode_Split = new CApiBuiltinExecutable("PyUnicode_Split", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 316);
    public static final CApiBuiltinExecutable PyUnicode_Substring = new CApiBuiltinExecutable("PyUnicode_Substring", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t}, 317);
    public static final CApiBuiltinExecutable PyUnicode_Tailmatch = new CApiBuiltinExecutable("PyUnicode_Tailmatch", CApiCallPath.Direct, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.Py_ssize_t, ArgDescriptor.Int}, 318);
    public static final CApiBuiltinExecutable PyWeakref_GetObject = new CApiBuiltinExecutable("PyWeakref_GetObject", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyObject}, 319);
    public static final CApiBuiltinExecutable PyWeakref_NewRef = new CApiBuiltinExecutable("PyWeakref_NewRef", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 320);
    public static final CApiBuiltinExecutable Py_AtExit = new CApiBuiltinExecutable("Py_AtExit", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.func_voidvoid}, 321);
    public static final CApiBuiltinExecutable Py_CompileString = new CApiBuiltinExecutable("Py_CompileString", CApiCallPath.Direct, ArgDescriptor.PyObject, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int}, 322);
    public static final CApiBuiltinExecutable Py_CompileStringExFlags = new CApiBuiltinExecutable("Py_CompileStringExFlags", CApiCallPath.Direct, ArgDescriptor.PyObject, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int, ArgDescriptor.PY_COMPILER_FLAGS, ArgDescriptor.Int}, 323);
    public static final CApiBuiltinExecutable Py_CompileStringObject = new CApiBuiltinExecutable("Py_CompileStringObject", CApiCallPath.Direct, ArgDescriptor.PyObject, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PyObject, ArgDescriptor.Int, ArgDescriptor.PY_COMPILER_FLAGS, ArgDescriptor.Int}, 324);
    public static final CApiBuiltinExecutable Py_EnterRecursiveCall = new CApiBuiltinExecutable("Py_EnterRecursiveCall", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.ConstCharPtr}, 325);
    public static final CApiBuiltinExecutable Py_GenericAlias = new CApiBuiltinExecutable("Py_GenericAlias", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 326);
    public static final CApiBuiltinExecutable Py_LeaveRecursiveCall = new CApiBuiltinExecutable("Py_LeaveRecursiveCall", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{}, 327);
    public static final CApiBuiltinExecutable Py_get_PyASCIIObject_length = new CApiBuiltinExecutable("Py_get_PyASCIIObject_length", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyASCIIObject}, 328);
    public static final CApiBuiltinExecutable Py_get_PyASCIIObject_state_ascii = new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_ascii", CApiCallPath.Ignored, ArgDescriptor.UNSIGNED_INT, new ArgDescriptor[]{ArgDescriptor.PyASCIIObject}, 329);
    public static final CApiBuiltinExecutable Py_get_PyASCIIObject_state_compact = new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_compact", CApiCallPath.Ignored, ArgDescriptor.UNSIGNED_INT, new ArgDescriptor[]{ArgDescriptor.PyASCIIObject}, 330);
    public static final CApiBuiltinExecutable Py_get_PyASCIIObject_state_kind = new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_kind", CApiCallPath.Ignored, ArgDescriptor.UNSIGNED_INT, new ArgDescriptor[]{ArgDescriptor.PyASCIIObject}, 331);
    public static final CApiBuiltinExecutable Py_get_PyASCIIObject_state_ready = new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_ready", CApiCallPath.Ignored, ArgDescriptor.UNSIGNED_INT, new ArgDescriptor[]{ArgDescriptor.PyASCIIObject}, 332);
    public static final CApiBuiltinExecutable Py_get_PyASCIIObject_wstr = new CApiBuiltinExecutable("Py_get_PyASCIIObject_wstr", CApiCallPath.Ignored, ArgDescriptor.WCHAR_T_PTR, new ArgDescriptor[]{ArgDescriptor.PyASCIIObject}, 333);
    public static final CApiBuiltinExecutable Py_get_PyAsyncMethods_am_aiter = new CApiBuiltinExecutable("Py_get_PyAsyncMethods_am_aiter", CApiCallPath.Ignored, ArgDescriptor.unaryfunc, new ArgDescriptor[]{ArgDescriptor.PyAsyncMethods}, 334);
    public static final CApiBuiltinExecutable Py_get_PyAsyncMethods_am_anext = new CApiBuiltinExecutable("Py_get_PyAsyncMethods_am_anext", CApiCallPath.Ignored, ArgDescriptor.unaryfunc, new ArgDescriptor[]{ArgDescriptor.PyAsyncMethods}, 335);
    public static final CApiBuiltinExecutable Py_get_PyAsyncMethods_am_await = new CApiBuiltinExecutable("Py_get_PyAsyncMethods_am_await", CApiCallPath.Ignored, ArgDescriptor.unaryfunc, new ArgDescriptor[]{ArgDescriptor.PyAsyncMethods}, 336);
    public static final CApiBuiltinExecutable Py_get_PyBufferProcs_bf_getbuffer = new CApiBuiltinExecutable("Py_get_PyBufferProcs_bf_getbuffer", CApiCallPath.Ignored, ArgDescriptor.getbufferproc, new ArgDescriptor[]{ArgDescriptor.PyBufferProcs}, 337);
    public static final CApiBuiltinExecutable Py_get_PyBufferProcs_bf_releasebuffer = new CApiBuiltinExecutable("Py_get_PyBufferProcs_bf_releasebuffer", CApiCallPath.Ignored, ArgDescriptor.releasebufferproc, new ArgDescriptor[]{ArgDescriptor.PyBufferProcs}, 338);
    public static final CApiBuiltinExecutable Py_get_PyByteArrayObject_ob_exports = new CApiBuiltinExecutable("Py_get_PyByteArrayObject_ob_exports", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyByteArrayObject}, 339);
    public static final CApiBuiltinExecutable Py_get_PyByteArrayObject_ob_start = new CApiBuiltinExecutable("Py_get_PyByteArrayObject_ob_start", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyByteArrayObject}, 340);
    public static final CApiBuiltinExecutable Py_get_PyCFunctionObject_m_ml = new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_ml", CApiCallPath.Ignored, ArgDescriptor.PyMethodDef, new ArgDescriptor[]{ArgDescriptor.PyCFunctionObject}, 341);
    public static final CApiBuiltinExecutable Py_get_PyCFunctionObject_m_module = new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_module", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyCFunctionObject}, 342);
    public static final CApiBuiltinExecutable Py_get_PyCFunctionObject_m_self = new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_self", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyCFunctionObject}, 343);
    public static final CApiBuiltinExecutable Py_get_PyCFunctionObject_m_weakreflist = new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_weakreflist", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyCFunctionObject}, 344);
    public static final CApiBuiltinExecutable Py_get_PyCFunctionObject_vectorcall = new CApiBuiltinExecutable("Py_get_PyCFunctionObject_vectorcall", CApiCallPath.Ignored, ArgDescriptor.vectorcallfunc, new ArgDescriptor[]{ArgDescriptor.PyCFunctionObject}, 345);
    public static final CApiBuiltinExecutable Py_get_PyCMethodObject_mm_class = new CApiBuiltinExecutable("Py_get_PyCMethodObject_mm_class", CApiCallPath.Ignored, ArgDescriptor.PyTypeObject, new ArgDescriptor[]{ArgDescriptor.PyCMethodObject}, 346);
    public static final CApiBuiltinExecutable Py_get_PyCompactUnicodeObject_wstr_length = new CApiBuiltinExecutable("Py_get_PyCompactUnicodeObject_wstr_length", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyCompactUnicodeObject}, 347);
    public static final CApiBuiltinExecutable Py_get_PyDescrObject_d_name = new CApiBuiltinExecutable("Py_get_PyDescrObject_d_name", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyDescrObject}, 348);
    public static final CApiBuiltinExecutable Py_get_PyDescrObject_d_type = new CApiBuiltinExecutable("Py_get_PyDescrObject_d_type", CApiCallPath.Ignored, ArgDescriptor.PyTypeObject, new ArgDescriptor[]{ArgDescriptor.PyDescrObject}, 349);
    public static final CApiBuiltinExecutable Py_get_PyFrameObject_f_lineno = new CApiBuiltinExecutable("Py_get_PyFrameObject_f_lineno", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyFrameObject}, 350);
    public static final CApiBuiltinExecutable Py_get_PyGetSetDef_closure = new CApiBuiltinExecutable("Py_get_PyGetSetDef_closure", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyGetSetDef}, 351);
    public static final CApiBuiltinExecutable Py_get_PyGetSetDef_doc = new CApiBuiltinExecutable("Py_get_PyGetSetDef_doc", CApiCallPath.Ignored, ArgDescriptor.ConstCharPtrAsTruffleString, new ArgDescriptor[]{ArgDescriptor.PyGetSetDef}, 352);
    public static final CApiBuiltinExecutable Py_get_PyGetSetDef_get = new CApiBuiltinExecutable("Py_get_PyGetSetDef_get", CApiCallPath.Ignored, ArgDescriptor.getter, new ArgDescriptor[]{ArgDescriptor.PyGetSetDef}, 353);
    public static final CApiBuiltinExecutable Py_get_PyGetSetDef_name = new CApiBuiltinExecutable("Py_get_PyGetSetDef_name", CApiCallPath.Ignored, ArgDescriptor.ConstCharPtrAsTruffleString, new ArgDescriptor[]{ArgDescriptor.PyGetSetDef}, 354);
    public static final CApiBuiltinExecutable Py_get_PyGetSetDef_set = new CApiBuiltinExecutable("Py_get_PyGetSetDef_set", CApiCallPath.Ignored, ArgDescriptor.setter, new ArgDescriptor[]{ArgDescriptor.PyGetSetDef}, 355);
    public static final CApiBuiltinExecutable Py_get_PyInstanceMethodObject_func = new CApiBuiltinExecutable("Py_get_PyInstanceMethodObject_func", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyInstanceMethodObject}, 356);
    public static final CApiBuiltinExecutable Py_get_PyListObject_ob_item = new CApiBuiltinExecutable("Py_get_PyListObject_ob_item", CApiCallPath.Ignored, ArgDescriptor.PyObjectPtr, new ArgDescriptor[]{ArgDescriptor.PyListObject}, 357);
    public static final CApiBuiltinExecutable Py_get_PyLongObject_ob_digit = new CApiBuiltinExecutable("Py_get_PyLongObject_ob_digit", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyLongObject}, 358);
    public static final CApiBuiltinExecutable Py_get_PyMappingMethods_mp_ass_subscript = new CApiBuiltinExecutable("Py_get_PyMappingMethods_mp_ass_subscript", CApiCallPath.Ignored, ArgDescriptor.objobjargproc, new ArgDescriptor[]{ArgDescriptor.PyMappingMethods}, 359);
    public static final CApiBuiltinExecutable Py_get_PyMappingMethods_mp_length = new CApiBuiltinExecutable("Py_get_PyMappingMethods_mp_length", CApiCallPath.Ignored, ArgDescriptor.lenfunc, new ArgDescriptor[]{ArgDescriptor.PyMappingMethods}, 360);
    public static final CApiBuiltinExecutable Py_get_PyMappingMethods_mp_subscript = new CApiBuiltinExecutable("Py_get_PyMappingMethods_mp_subscript", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyMappingMethods}, 361);
    public static final CApiBuiltinExecutable Py_get_PyMethodDef_ml_doc = new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_doc", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyMethodDef}, 362);
    public static final CApiBuiltinExecutable Py_get_PyMethodDef_ml_flags = new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_flags", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyMethodDef}, 363);
    public static final CApiBuiltinExecutable Py_get_PyMethodDef_ml_meth = new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_meth", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyMethodDef}, 364);
    public static final CApiBuiltinExecutable Py_get_PyMethodDef_ml_name = new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_name", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyMethodDef}, 365);
    public static final CApiBuiltinExecutable Py_get_PyMethodDescrObject_d_method = new CApiBuiltinExecutable("Py_get_PyMethodDescrObject_d_method", CApiCallPath.Ignored, ArgDescriptor.PyMethodDef, new ArgDescriptor[]{ArgDescriptor.PyMethodDescrObject}, 366);
    public static final CApiBuiltinExecutable Py_get_PyMethodObject_im_func = new CApiBuiltinExecutable("Py_get_PyMethodObject_im_func", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyMethodObject}, 367);
    public static final CApiBuiltinExecutable Py_get_PyMethodObject_im_self = new CApiBuiltinExecutable("Py_get_PyMethodObject_im_self", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyMethodObject}, 368);
    public static final CApiBuiltinExecutable Py_get_PyModuleDef_m_doc = new CApiBuiltinExecutable("Py_get_PyModuleDef_m_doc", CApiCallPath.Ignored, ArgDescriptor.ConstCharPtrAsTruffleString, new ArgDescriptor[]{ArgDescriptor.PyModuleDef}, 369);
    public static final CApiBuiltinExecutable Py_get_PyModuleDef_m_methods = new CApiBuiltinExecutable("Py_get_PyModuleDef_m_methods", CApiCallPath.Ignored, ArgDescriptor.PyMethodDef, new ArgDescriptor[]{ArgDescriptor.PyModuleDef}, 370);
    public static final CApiBuiltinExecutable Py_get_PyModuleDef_m_name = new CApiBuiltinExecutable("Py_get_PyModuleDef_m_name", CApiCallPath.Ignored, ArgDescriptor.ConstCharPtrAsTruffleString, new ArgDescriptor[]{ArgDescriptor.PyModuleDef}, 371);
    public static final CApiBuiltinExecutable Py_get_PyModuleDef_m_size = new CApiBuiltinExecutable("Py_get_PyModuleDef_m_size", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyModuleDef}, 372);
    public static final CApiBuiltinExecutable Py_get_PyModuleObject_md_def = new CApiBuiltinExecutable("Py_get_PyModuleObject_md_def", CApiCallPath.Ignored, ArgDescriptor.PyModuleDef, new ArgDescriptor[]{ArgDescriptor.PyModuleObject}, 373);
    public static final CApiBuiltinExecutable Py_get_PyModuleObject_md_dict = new CApiBuiltinExecutable("Py_get_PyModuleObject_md_dict", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyModuleObject}, 374);
    public static final CApiBuiltinExecutable Py_get_PyModuleObject_md_state = new CApiBuiltinExecutable("Py_get_PyModuleObject_md_state", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyModuleObject}, 375);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_absolute = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_absolute", CApiCallPath.Ignored, ArgDescriptor.unaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 376);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_add = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_add", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 377);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_and = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_and", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 378);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_bool = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_bool", CApiCallPath.Ignored, ArgDescriptor.inquiry, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 379);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_divmod = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_divmod", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 380);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_float = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_float", CApiCallPath.Ignored, ArgDescriptor.unaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 381);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_floor_divide = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_floor_divide", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 382);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_index = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_index", CApiCallPath.Ignored, ArgDescriptor.unaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 383);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_add = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_add", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 384);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_and = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_and", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 385);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_floor_divide = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_floor_divide", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 386);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_lshift = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_lshift", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 387);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_matrix_multiply = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_matrix_multiply", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 388);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_multiply = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_multiply", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 389);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_or = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_or", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 390);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_power = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_power", CApiCallPath.Ignored, ArgDescriptor.ternaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 391);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_remainder = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_remainder", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 392);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_rshift = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_rshift", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 393);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_subtract = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_subtract", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 394);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_true_divide = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_true_divide", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 395);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_inplace_xor = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_xor", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 396);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_int = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_int", CApiCallPath.Ignored, ArgDescriptor.unaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 397);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_invert = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_invert", CApiCallPath.Ignored, ArgDescriptor.unaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 398);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_lshift = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_lshift", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 399);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_matrix_multiply = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_matrix_multiply", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 400);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_multiply = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_multiply", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 401);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_negative = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_negative", CApiCallPath.Ignored, ArgDescriptor.unaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 402);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_or = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_or", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 403);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_positive = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_positive", CApiCallPath.Ignored, ArgDescriptor.unaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 404);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_power = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_power", CApiCallPath.Ignored, ArgDescriptor.ternaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 405);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_remainder = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_remainder", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 406);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_rshift = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_rshift", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 407);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_subtract = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_subtract", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 408);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_true_divide = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_true_divide", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 409);
    public static final CApiBuiltinExecutable Py_get_PyNumberMethods_nb_xor = new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_xor", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PyNumberMethods}, 410);
    public static final CApiBuiltinExecutable Py_get_PyObject_ob_refcnt = new CApiBuiltinExecutable("Py_get_PyObject_ob_refcnt", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyObjectWrapper}, 411);
    public static final CApiBuiltinExecutable Py_get_PyObject_ob_type = new CApiBuiltinExecutable("Py_get_PyObject_ob_type", CApiCallPath.Ignored, ArgDescriptor.PyTypeObject, new ArgDescriptor[]{ArgDescriptor.PyObject}, 412);
    public static final CApiBuiltinExecutable Py_get_PySequenceMethods_sq_ass_item = new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_ass_item", CApiCallPath.Ignored, ArgDescriptor.ssizeobjargproc, new ArgDescriptor[]{ArgDescriptor.PySequenceMethods}, 413);
    public static final CApiBuiltinExecutable Py_get_PySequenceMethods_sq_concat = new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_concat", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PySequenceMethods}, 414);
    public static final CApiBuiltinExecutable Py_get_PySequenceMethods_sq_contains = new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_contains", CApiCallPath.Ignored, ArgDescriptor.objobjproc, new ArgDescriptor[]{ArgDescriptor.PySequenceMethods}, 415);
    public static final CApiBuiltinExecutable Py_get_PySequenceMethods_sq_inplace_concat = new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_inplace_concat", CApiCallPath.Ignored, ArgDescriptor.binaryfunc, new ArgDescriptor[]{ArgDescriptor.PySequenceMethods}, 416);
    public static final CApiBuiltinExecutable Py_get_PySequenceMethods_sq_inplace_repeat = new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_inplace_repeat", CApiCallPath.Ignored, ArgDescriptor.ssizeargfunc, new ArgDescriptor[]{ArgDescriptor.PySequenceMethods}, 417);
    public static final CApiBuiltinExecutable Py_get_PySequenceMethods_sq_item = new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_item", CApiCallPath.Ignored, ArgDescriptor.ssizeargfunc, new ArgDescriptor[]{ArgDescriptor.PySequenceMethods}, 418);
    public static final CApiBuiltinExecutable Py_get_PySequenceMethods_sq_length = new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_length", CApiCallPath.Ignored, ArgDescriptor.lenfunc, new ArgDescriptor[]{ArgDescriptor.PySequenceMethods}, 419);
    public static final CApiBuiltinExecutable Py_get_PySequenceMethods_sq_repeat = new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_repeat", CApiCallPath.Ignored, ArgDescriptor.ssizeargfunc, new ArgDescriptor[]{ArgDescriptor.PySequenceMethods}, 420);
    public static final CApiBuiltinExecutable Py_get_PySetObject_used = new CApiBuiltinExecutable("Py_get_PySetObject_used", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PySetObject}, 421);
    public static final CApiBuiltinExecutable Py_get_PySliceObject_start = new CApiBuiltinExecutable("Py_get_PySliceObject_start", CApiCallPath.Ignored, ArgDescriptor.PyObject, new ArgDescriptor[]{ArgDescriptor.PySliceObject}, 422);
    public static final CApiBuiltinExecutable Py_get_PySliceObject_step = new CApiBuiltinExecutable("Py_get_PySliceObject_step", CApiCallPath.Ignored, ArgDescriptor.PyObject, new ArgDescriptor[]{ArgDescriptor.PySliceObject}, 423);
    public static final CApiBuiltinExecutable Py_get_PySliceObject_stop = new CApiBuiltinExecutable("Py_get_PySliceObject_stop", CApiCallPath.Ignored, ArgDescriptor.PyObject, new ArgDescriptor[]{ArgDescriptor.PySliceObject}, 424);
    public static final CApiBuiltinExecutable Py_get_PyThreadState_dict = new CApiBuiltinExecutable("Py_get_PyThreadState_dict", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyThreadState}, 425);
    public static final CApiBuiltinExecutable Py_get_PyTupleObject_ob_item = new CApiBuiltinExecutable("Py_get_PyTupleObject_ob_item", CApiCallPath.Ignored, ArgDescriptor.PyObjectPtr, new ArgDescriptor[]{ArgDescriptor.PyTupleObject}, 426);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_alloc = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_alloc", CApiCallPath.Ignored, ArgDescriptor.allocfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 427);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_as_async = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_async", CApiCallPath.Ignored, ArgDescriptor.PyAsyncMethods, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 428);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_as_buffer = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_buffer", CApiCallPath.Ignored, ArgDescriptor.PyBufferProcs, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 429);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_as_mapping = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_mapping", CApiCallPath.Ignored, ArgDescriptor.PyMappingMethods, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 430);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_as_number = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_number", CApiCallPath.Ignored, ArgDescriptor.PyNumberMethods, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 431);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_as_sequence = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_sequence", CApiCallPath.Ignored, ArgDescriptor.PySequenceMethods, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 432);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_base = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_base", CApiCallPath.Ignored, ArgDescriptor.PyTypeObject, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 433);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_bases = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_bases", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 434);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_basicsize = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_basicsize", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 435);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_cache = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_cache", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 436);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_call = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_call", CApiCallPath.Ignored, ArgDescriptor.ternaryfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 437);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_clear = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_clear", CApiCallPath.Ignored, ArgDescriptor.inquiry, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 438);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_dealloc = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_dealloc", CApiCallPath.Ignored, ArgDescriptor.destructor, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 439);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_del = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_del", CApiCallPath.Ignored, ArgDescriptor.destructor, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 440);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_descr_get = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_descr_get", CApiCallPath.Ignored, ArgDescriptor.descrgetfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 441);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_descr_set = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_descr_set", CApiCallPath.Ignored, ArgDescriptor.descrsetfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 442);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_dict = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_dict", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 443);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_dictoffset = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_dictoffset", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 444);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_doc = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_doc", CApiCallPath.Ignored, ArgDescriptor.ConstCharPtrAsTruffleString, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 445);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_finalize = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_finalize", CApiCallPath.Ignored, ArgDescriptor.destructor, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 446);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_flags = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_flags", CApiCallPath.Ignored, ArgDescriptor.UNSIGNED_LONG, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 447);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_free = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_free", CApiCallPath.Ignored, ArgDescriptor.freefunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 448);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_getattr = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_getattr", CApiCallPath.Ignored, ArgDescriptor.getattrfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 449);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_getattro = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_getattro", CApiCallPath.Ignored, ArgDescriptor.getattrofunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 450);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_getset = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_getset", CApiCallPath.Ignored, ArgDescriptor.PyGetSetDef, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 451);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_hash = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_hash", CApiCallPath.Ignored, ArgDescriptor.hashfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 452);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_init = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_init", CApiCallPath.Ignored, ArgDescriptor.initproc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 453);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_is_gc = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_is_gc", CApiCallPath.Ignored, ArgDescriptor.inquiry, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 454);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_itemsize = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_itemsize", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 455);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_iter = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_iter", CApiCallPath.Ignored, ArgDescriptor.getiterfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 456);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_iternext = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_iternext", CApiCallPath.Ignored, ArgDescriptor.iternextfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 457);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_members = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_members", CApiCallPath.Ignored, ArgDescriptor.PyMemberDef, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 458);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_methods = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_methods", CApiCallPath.Ignored, ArgDescriptor.PyMethodDef, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 459);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_mro = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_mro", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 460);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_name = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_name", CApiCallPath.Ignored, ArgDescriptor.ConstCharPtrAsTruffleString, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 461);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_new = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_new", CApiCallPath.Ignored, ArgDescriptor.newfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 462);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_repr = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_repr", CApiCallPath.Ignored, ArgDescriptor.reprfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 463);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_richcompare = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_richcompare", CApiCallPath.Ignored, ArgDescriptor.richcmpfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 464);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_setattr = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_setattr", CApiCallPath.Ignored, ArgDescriptor.setattrfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 465);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_setattro = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_setattro", CApiCallPath.Ignored, ArgDescriptor.setattrofunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 466);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_str = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_str", CApiCallPath.Ignored, ArgDescriptor.reprfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 467);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_subclasses = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_subclasses", CApiCallPath.Ignored, ArgDescriptor.PyObject, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 468);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_traverse = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_traverse", CApiCallPath.Ignored, ArgDescriptor.traverseproc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 469);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_vectorcall = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_vectorcall", CApiCallPath.Ignored, ArgDescriptor.vectorcallfunc, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 470);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_vectorcall_offset = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_vectorcall_offset", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 471);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_version_tag = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_version_tag", CApiCallPath.Ignored, ArgDescriptor.UNSIGNED_INT, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 472);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_weaklist = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_weaklist", CApiCallPath.Ignored, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 473);
    public static final CApiBuiltinExecutable Py_get_PyTypeObject_tp_weaklistoffset = new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_weaklistoffset", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyTypeObject}, 474);
    public static final CApiBuiltinExecutable Py_get_PyUnicodeObject_data = new CApiBuiltinExecutable("Py_get_PyUnicodeObject_data", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.PyUnicodeObject}, 475);
    public static final CApiBuiltinExecutable Py_get_PyVarObject_ob_size = new CApiBuiltinExecutable("Py_get_PyVarObject_ob_size", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{ArgDescriptor.PyVarObject}, 476);
    public static final CApiBuiltinExecutable Py_get_dummy = new CApiBuiltinExecutable("Py_get_dummy", CApiCallPath.Ignored, ArgDescriptor.Pointer, new ArgDescriptor[]{ArgDescriptor.Pointer}, 477);
    public static final CApiBuiltinExecutable Py_get_mmap_object_data = new CApiBuiltinExecutable("Py_get_mmap_object_data", CApiCallPath.Ignored, ArgDescriptor.CHAR_PTR, new ArgDescriptor[]{ArgDescriptor.mmap_object}, 478);
    public static final CApiBuiltinExecutable Py_set_PyByteArrayObject_ob_exports = new CApiBuiltinExecutable("Py_set_PyByteArrayObject_ob_exports", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyByteArrayObject, ArgDescriptor.Int}, 479);
    public static final CApiBuiltinExecutable Py_set_PyFrameObject_f_lineno = new CApiBuiltinExecutable("Py_set_PyFrameObject_f_lineno", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyFrameObject, ArgDescriptor.Int}, 480);
    public static final CApiBuiltinExecutable Py_set_PyModuleObject_md_def = new CApiBuiltinExecutable("Py_set_PyModuleObject_md_def", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyModuleObject, ArgDescriptor.PyModuleDef}, 481);
    public static final CApiBuiltinExecutable Py_set_PyModuleObject_md_state = new CApiBuiltinExecutable("Py_set_PyModuleObject_md_state", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyModuleObject, ArgDescriptor.Pointer}, 482);
    public static final CApiBuiltinExecutable Py_set_PyObject_ob_refcnt = new CApiBuiltinExecutable("Py_set_PyObject_ob_refcnt", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObjectWrapper, ArgDescriptor.Py_ssize_t}, 483);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_alloc = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_alloc", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.allocfunc}, 484);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_as_buffer = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_as_buffer", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyBufferProcs}, 485);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_base = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_base", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyTypeObject}, 486);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_bases = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_bases", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyObject}, 487);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_basicsize = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_basicsize", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.Py_ssize_t}, 488);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_clear = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_clear", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.inquiry}, 489);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_dealloc = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_dealloc", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.destructor}, 490);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_dict = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_dict", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyObject}, 491);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_dictoffset = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_dictoffset", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.Py_ssize_t}, 492);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_finalize = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_finalize", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.destructor}, 493);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_flags = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_flags", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.UNSIGNED_LONG}, 494);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_free = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_free", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.freefunc}, 495);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_getattr = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_getattr", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.getattrfunc}, 496);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_getattro = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_getattro", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.getattrofunc}, 497);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_itemsize = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_itemsize", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.Py_ssize_t}, 498);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_iter = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_iter", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.getiterfunc}, 499);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_iternext = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_iternext", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.iternextfunc}, 500);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_mro = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_mro", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyObject}, 501);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_new = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_new", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.newfunc}, 502);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_setattr = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_setattr", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.setattrfunc}, 503);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_setattro = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_setattro", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.setattrofunc}, 504);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_subclasses = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_subclasses", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyObject}, 505);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_traverse = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_traverse", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.traverseproc}, 506);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_vectorcall_offset = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_vectorcall_offset", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.Py_ssize_t}, 507);
    public static final CApiBuiltinExecutable Py_set_PyTypeObject_tp_weaklistoffset = new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_weaklistoffset", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.Py_ssize_t}, 508);
    public static final CApiBuiltinExecutable Py_set_PyVarObject_ob_size = new CApiBuiltinExecutable("Py_set_PyVarObject_ob_size", CApiCallPath.Ignored, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyVarObject, ArgDescriptor.Py_ssize_t}, 509);
    public static final CApiBuiltinExecutable _PyArray_Resize = new CApiBuiltinExecutable("_PyArray_Resize", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 510);
    public static final CApiBuiltinExecutable _PyBytes_Join = new CApiBuiltinExecutable("_PyBytes_Join", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 511);
    public static final CApiBuiltinExecutable _PyDict_Pop = new CApiBuiltinExecutable("_PyDict_Pop", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 512);
    public static final CApiBuiltinExecutable _PyDict_SetItem_KnownHash = new CApiBuiltinExecutable("_PyDict_SetItem_KnownHash", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Py_hash_t}, 513);
    public static final CApiBuiltinExecutable _PyErr_BadInternalCall = new CApiBuiltinExecutable("_PyErr_BadInternalCall", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int}, 514);
    public static final CApiBuiltinExecutable _PyErr_WriteUnraisableMsg = new CApiBuiltinExecutable("_PyErr_WriteUnraisableMsg", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PyObject}, 515);
    public static final CApiBuiltinExecutable _PyList_Extend = new CApiBuiltinExecutable("_PyList_Extend", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyListObject, ArgDescriptor.PyObject}, 516);
    public static final CApiBuiltinExecutable _PyList_SET_ITEM = new CApiBuiltinExecutable("_PyList_SET_ITEM", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.PyObject}, 517);
    public static final CApiBuiltinExecutable _PyLong_Sign = new CApiBuiltinExecutable("_PyLong_Sign", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 518);
    public static final CApiBuiltinExecutable _PyNamespace_New = new CApiBuiltinExecutable("_PyNamespace_New", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 519);
    public static final CApiBuiltinExecutable _PyNumber_Index = new CApiBuiltinExecutable("_PyNumber_Index", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject}, 520);
    public static final CApiBuiltinExecutable _PyObject_Dump = new CApiBuiltinExecutable("_PyObject_Dump", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObjectWrapper}, 521);
    public static final CApiBuiltinExecutable _PyTraceMalloc_NewReference = new CApiBuiltinExecutable("_PyTraceMalloc_NewReference", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject}, 522);
    public static final CApiBuiltinExecutable _PyTraceback_Add = new CApiBuiltinExecutable("_PyTraceback_Add", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.Int}, 523);
    public static final CApiBuiltinExecutable _PyTruffleBytes_Resize = new CApiBuiltinExecutable("_PyTruffleBytes_Resize", CApiCallPath.Ignored, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 524);
    public static final CApiBuiltinExecutable _PyTruffleErr_CreateAndSetException = new CApiBuiltinExecutable("_PyTruffleErr_CreateAndSetException", CApiCallPath.Direct, ArgDescriptor.Void, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 525);
    public static final CApiBuiltinExecutable _PyTruffleErr_Warn = new CApiBuiltinExecutable("_PyTruffleErr_Warn", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.PyObject}, 526);
    public static final CApiBuiltinExecutable _PyTruffleEval_EvalCodeEx = new CApiBuiltinExecutable("_PyTruffleEval_EvalCodeEx", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Pointer, ArgDescriptor.Pointer, ArgDescriptor.Pointer, ArgDescriptor.PyObject, ArgDescriptor.PyObject}, 527);
    public static final CApiBuiltinExecutable _PyTruffleModule_CreateInitialized_PyModule_New = new CApiBuiltinExecutable("_PyTruffleModule_CreateInitialized_PyModule_New", CApiCallPath.Ignored, ArgDescriptor.PyModuleObjectTransfer, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 528);
    public static final CApiBuiltinExecutable _PyTruffleModule_GetAndIncMaxModuleNumber = new CApiBuiltinExecutable("_PyTruffleModule_GetAndIncMaxModuleNumber", CApiCallPath.Ignored, ArgDescriptor.Py_ssize_t, new ArgDescriptor[]{}, 529);
    public static final CApiBuiltinExecutable _PyTruffleObject_Call1 = new CApiBuiltinExecutable("_PyTruffleObject_Call1", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.PyObject, ArgDescriptor.Int}, 530);
    public static final CApiBuiltinExecutable _PyTruffleObject_CallMethod1 = new CApiBuiltinExecutable("_PyTruffleObject_CallMethod1", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString, ArgDescriptor.PyObject, ArgDescriptor.Int}, 531);
    public static final CApiBuiltinExecutable _PyTruffleObject_MakeTpCall = new CApiBuiltinExecutable("_PyTruffleObject_MakeTpCall", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Pointer, ArgDescriptor.Int, ArgDescriptor.Pointer, ArgDescriptor.Pointer}, 532);
    public static final CApiBuiltinExecutable _PyTruffleSet_NextEntry = new CApiBuiltinExecutable("_PyTruffleSet_NextEntry", CApiCallPath.Ignored, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t}, 533);
    public static final CApiBuiltinExecutable _PyTruffle_HashBytes = new CApiBuiltinExecutable("_PyTruffle_HashBytes", CApiCallPath.Ignored, ArgDescriptor.Py_hash_t, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 534);
    public static final CApiBuiltinExecutable _PyTuple_SET_ITEM = new CApiBuiltinExecutable("_PyTuple_SET_ITEM", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Py_ssize_t, ArgDescriptor.PyObjectTransfer}, 535);
    public static final CApiBuiltinExecutable _PyType_Lookup = new CApiBuiltinExecutable("_PyType_Lookup", CApiCallPath.Direct, ArgDescriptor.PyObjectBorrowed, new ArgDescriptor[]{ArgDescriptor.PyTypeObject, ArgDescriptor.PyObject}, 536);
    public static final CApiBuiltinExecutable _PyUnicode_AsASCIIString = new CApiBuiltinExecutable("_PyUnicode_AsASCIIString", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 537);
    public static final CApiBuiltinExecutable _PyUnicode_AsLatin1String = new CApiBuiltinExecutable("_PyUnicode_AsLatin1String", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 538);
    public static final CApiBuiltinExecutable _PyUnicode_AsUTF8String = new CApiBuiltinExecutable("_PyUnicode_AsUTF8String", CApiCallPath.Direct, ArgDescriptor.PyObjectTransfer, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 539);
    public static final CApiBuiltinExecutable _PyUnicode_EqualToASCIIString = new CApiBuiltinExecutable("_PyUnicode_EqualToASCIIString", CApiCallPath.Direct, ArgDescriptor.Int, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.ConstCharPtrAsTruffleString}, 540);
    public static final CApiBuiltinExecutable _Py_GetErrorHandler = new CApiBuiltinExecutable("_Py_GetErrorHandler", CApiCallPath.Direct, ArgDescriptor._PY_ERROR_HANDLER, new ArgDescriptor[]{ArgDescriptor.ConstCharPtrAsTruffleString}, 541);
    public static final CApiBuiltinExecutable _Py_HashDouble = new CApiBuiltinExecutable("_Py_HashDouble", CApiCallPath.Direct, ArgDescriptor.Py_hash_t, new ArgDescriptor[]{ArgDescriptor.PyObject, ArgDescriptor.Double}, 542);

    public static final CApiBuiltinExecutable[] builtins = {
                    PyByteArray_Resize,
                    PyBytes_FromObject,
                    PyBytes_Size,
                    PyCallIter_New,
                    PyCallable_Check,
                    PyCapsule_GetContext,
                    PyCapsule_GetDestructor,
                    PyCapsule_GetName,
                    PyCapsule_GetPointer,
                    PyCapsule_Import,
                    PyCapsule_IsValid,
                    PyCapsule_New,
                    PyCapsule_SetContext,
                    PyCapsule_SetDestructor,
                    PyCapsule_SetName,
                    PyCapsule_SetPointer,
                    PyClassMethod_New,
                    PyCode_Addr2Line,
                    PyCode_GetFileName,
                    PyCode_GetName,
                    PyCode_New,
                    PyCode_NewEmpty,
                    PyCode_NewWithPosOnlyArgs,
                    PyCodec_Decoder,
                    PyCodec_Encoder,
                    PyComplex_FromDoubles,
                    PyComplex_ImagAsDouble,
                    PyComplex_RealAsDouble,
                    PyContextVar_New,
                    PyContextVar_Set,
                    PyDictProxy_New,
                    PyDict_Clear,
                    PyDict_Contains,
                    PyDict_Copy,
                    PyDict_DelItem,
                    PyDict_GetItem,
                    PyDict_GetItemWithError,
                    PyDict_Items,
                    PyDict_Keys,
                    PyDict_Merge,
                    PyDict_New,
                    PyDict_SetDefault,
                    PyDict_SetItem,
                    PyDict_Size,
                    PyDict_Update,
                    PyDict_Values,
                    PyErr_Display,
                    PyErr_GivenExceptionMatches,
                    PyErr_NewException,
                    PyErr_NewExceptionWithDoc,
                    PyErr_Occurred,
                    PyErr_PrintEx,
                    PyErr_Restore,
                    PyErr_SetExcInfo,
                    PyEval_GetBuiltins,
                    PyEval_GetFrame,
                    PyEval_RestoreThread,
                    PyEval_SaveThread,
                    PyException_GetCause,
                    PyException_GetContext,
                    PyException_SetCause,
                    PyException_SetContext,
                    PyException_SetTraceback,
                    PyFile_WriteObject,
                    PyFloat_FromDouble,
                    PyFloat_FromString,
                    PyFrame_GetBack,
                    PyFrame_GetBuiltins,
                    PyFrame_GetCode,
                    PyFrame_GetGlobals,
                    PyFrame_GetLasti,
                    PyFrame_GetLineNumber,
                    PyFrame_GetLocals,
                    PyFrame_New,
                    PyFrozenSet_New,
                    PyGILState_Check,
                    PyImport_GetModuleDict,
                    PyImport_Import,
                    PyImport_ImportModule,
                    PyImport_ImportModuleLevelObject,
                    PyImport_ImportModuleNoBlock,
                    PyIndex_Check,
                    PyInstanceMethod_New,
                    PyIter_Check,
                    PyIter_Next,
                    PyList_Append,
                    PyList_AsTuple,
                    PyList_GetItem,
                    PyList_GetSlice,
                    PyList_Insert,
                    PyList_New,
                    PyList_Reverse,
                    PyList_SetItem,
                    PyList_SetSlice,
                    PyList_Size,
                    PyList_Sort,
                    PyLong_AsVoidPtr,
                    PyLong_FromDouble,
                    PyLong_FromLong,
                    PyLong_FromLongLong,
                    PyLong_FromSize_t,
                    PyLong_FromSsize_t,
                    PyLong_FromUnsignedLong,
                    PyLong_FromUnsignedLongLong,
                    PyMapping_Check,
                    PyMapping_Items,
                    PyMapping_Keys,
                    PyMapping_Size,
                    PyMapping_Values,
                    PyMemoryView_FromObject,
                    PyMemoryView_GetContiguous,
                    PyMethod_New,
                    PyModule_AddIntConstant,
                    PyModule_AddObjectRef,
                    PyModule_GetNameObject,
                    PyModule_New,
                    PyModule_NewObject,
                    PyModule_SetDocString,
                    PyNumber_Absolute,
                    PyNumber_Check,
                    PyNumber_Divmod,
                    PyNumber_Float,
                    PyNumber_InPlacePower,
                    PyNumber_Index,
                    PyNumber_Long,
                    PyNumber_Power,
                    PyNumber_ToBase,
                    PyOS_FSPath,
                    PyObject_ASCII,
                    PyObject_AsFileDescriptor,
                    PyObject_Bytes,
                    PyObject_ClearWeakRefs,
                    PyObject_DelItem,
                    PyObject_Dir,
                    PyObject_Format,
                    PyObject_GC_Track,
                    PyObject_GC_UnTrack,
                    PyObject_GetDoc,
                    PyObject_GetItem,
                    PyObject_GetIter,
                    PyObject_HasAttr,
                    PyObject_HasAttrString,
                    PyObject_Hash,
                    PyObject_HashNotImplemented,
                    PyObject_IsInstance,
                    PyObject_IsSubclass,
                    PyObject_IsTrue,
                    PyObject_LengthHint,
                    PyObject_Repr,
                    PyObject_RichCompare,
                    PyObject_SetDoc,
                    PyObject_SetItem,
                    PyObject_Size,
                    PyObject_Str,
                    PyObject_Type,
                    PyRun_StringFlags,
                    PySeqIter_New,
                    PySequence_Check,
                    PySequence_Concat,
                    PySequence_Contains,
                    PySequence_Count,
                    PySequence_DelItem,
                    PySequence_DelSlice,
                    PySequence_GetItem,
                    PySequence_GetSlice,
                    PySequence_InPlaceConcat,
                    PySequence_InPlaceRepeat,
                    PySequence_Index,
                    PySequence_Length,
                    PySequence_List,
                    PySequence_Repeat,
                    PySequence_SetItem,
                    PySequence_SetSlice,
                    PySequence_Size,
                    PySequence_Tuple,
                    PySet_Add,
                    PySet_Clear,
                    PySet_Contains,
                    PySet_Discard,
                    PySet_New,
                    PySet_Pop,
                    PySet_Size,
                    PySlice_New,
                    PyStaticMethod_New,
                    PyStructSequence_New,
                    PySys_GetObject,
                    PyThreadState_Get,
                    PyThreadState_GetDict,
                    PyThread_acquire_lock,
                    PyThread_allocate_lock,
                    PyThread_get_thread_ident,
                    PyThread_release_lock,
                    PyTraceBack_Here,
                    PyTraceMalloc_Track,
                    PyTraceMalloc_Untrack,
                    PyTruffleByteArray_FromStringAndSize,
                    PyTruffleBytes_Concat,
                    PyTruffleBytes_FromFormat,
                    PyTruffleBytes_FromStringAndSize,
                    PyTruffleCMethod_NewEx,
                    PyTruffleComplex_AsCComplex,
                    PyTruffleContextVar_Get,
                    PyTruffleDateTimeCAPI_DateTime_FromDateAndTime,
                    PyTruffleDateTimeCAPI_DateTime_FromDateAndTimeAndFold,
                    PyTruffleDateTimeCAPI_DateTime_FromTimestamp,
                    PyTruffleDateTimeCAPI_Date_FromDate,
                    PyTruffleDateTimeCAPI_Date_FromTimestamp,
                    PyTruffleDateTimeCAPI_Delta_FromDelta,
                    PyTruffleDateTimeCAPI_TimeZone_FromTimeZone,
                    PyTruffleDateTimeCAPI_Time_FromTime,
                    PyTruffleDateTimeCAPI_Time_FromTimeAndFold,
                    PyTruffleDescr_NewClassMethod,
                    PyTruffleDescr_NewGetSet,
                    PyTruffleDict_Next,
                    PyTruffleErr_Fetch,
                    PyTruffleErr_GetExcInfo,
                    PyTruffleErr_WarnExplicit,
                    PyTruffleFloat_AsDouble,
                    PyTruffleGILState_Ensure,
                    PyTruffleGILState_Release,
                    PyTruffleHash_InitSecret,
                    PyTruffleLong_AsPrimitive,
                    PyTruffleLong_FromString,
                    PyTruffleLong_One,
                    PyTruffleLong_Zero,
                    PyTruffleModule_AddFunctionToModule,
                    PyTruffleNumber_BinOp,
                    PyTruffleNumber_InPlaceBinOp,
                    PyTruffleNumber_UnaryOp,
                    PyTruffleObject_CallFunctionObjArgs,
                    PyTruffleObject_CallMethodObjArgs,
                    PyTruffleObject_GenericGetAttr,
                    PyTruffleObject_GenericSetAttr,
                    PyTruffleObject_GetItemString,
                    PyTruffleState_FindModule,
                    PyTruffleStructSequence_InitType2,
                    PyTruffleStructSequence_NewType,
                    PyTruffleToCharPointer,
                    PyTruffleType_AddFunctionToType,
                    PyTruffleType_AddGetSet,
                    PyTruffleType_AddMember,
                    PyTruffleType_AddSlot,
                    PyTruffleUnicode_Decode,
                    PyTruffleUnicode_DecodeUTF8Stateful,
                    PyTruffleUnicode_FromUCS,
                    PyTruffleUnicode_InternInPlace,
                    PyTruffleUnicode_New,
                    PyTruffle_Arg_ParseTupleAndKeywords,
                    PyTruffle_ByteArray_EmptyWithCapacity,
                    PyTruffle_Bytes_AsString,
                    PyTruffle_Bytes_CheckEmbeddedNull,
                    PyTruffle_Bytes_EmptyWithCapacity,
                    PyTruffle_Compute_Mro,
                    PyTruffle_Debug,
                    PyTruffle_DebugTrace,
                    PyTruffle_Ellipsis,
                    PyTruffle_False,
                    PyTruffle_FatalErrorFunc,
                    PyTruffle_FileSystemDefaultEncoding,
                    PyTruffle_Get_Inherited_Native_Slots,
                    PyTruffle_HashConstant,
                    PyTruffle_InitialNativeMemory,
                    PyTruffle_LogString,
                    PyTruffle_MaxNativeMemory,
                    PyTruffle_MemoryViewFromBuffer,
                    PyTruffle_Native_Options,
                    PyTruffle_NewTypeDict,
                    PyTruffle_NoValue,
                    PyTruffle_None,
                    PyTruffle_NotImplemented,
                    PyTruffle_Object_Free,
                    PyTruffle_PyDateTime_GET_TZINFO,
                    PyTruffle_Register_NULL,
                    PyTruffle_Set_Native_Slots,
                    PyTruffle_Set_SulongType,
                    PyTruffle_ToNative,
                    PyTruffle_Trace_Type,
                    PyTruffle_TriggerGC,
                    PyTruffle_True,
                    PyTruffle_Type,
                    PyTruffle_Type_Modified,
                    PyTruffle_Unicode_AsUTF8AndSize_CharPtr,
                    PyTruffle_Unicode_AsUTF8AndSize_Size,
                    PyTruffle_Unicode_AsUnicodeAndSize_CharPtr,
                    PyTruffle_Unicode_AsUnicodeAndSize_Size,
                    PyTruffle_Unicode_AsWideChar,
                    PyTruffle_Unicode_DecodeUTF32,
                    PyTruffle_Unicode_FromFormat,
                    PyTruffle_Unicode_FromWchar,
                    PyTruffle_tss_create,
                    PyTruffle_tss_delete,
                    PyTruffle_tss_get,
                    PyTruffle_tss_set,
                    PyTuple_GetItem,
                    PyTuple_GetSlice,
                    PyTuple_New,
                    PyTuple_SetItem,
                    PyTuple_Size,
                    PyType_IsSubtype,
                    PyUnicodeDecodeError_Create,
                    PyUnicode_AsEncodedString,
                    PyUnicode_AsUnicodeEscapeString,
                    PyUnicode_Compare,
                    PyUnicode_Concat,
                    PyUnicode_Contains,
                    PyUnicode_DecodeFSDefault,
                    PyUnicode_EncodeFSDefault,
                    PyUnicode_FindChar,
                    PyUnicode_Format,
                    PyUnicode_FromEncodedObject,
                    PyUnicode_FromObject,
                    PyUnicode_FromOrdinal,
                    PyUnicode_FromString,
                    PyUnicode_Join,
                    PyUnicode_ReadChar,
                    PyUnicode_Replace,
                    PyUnicode_Split,
                    PyUnicode_Substring,
                    PyUnicode_Tailmatch,
                    PyWeakref_GetObject,
                    PyWeakref_NewRef,
                    Py_AtExit,
                    Py_CompileString,
                    Py_CompileStringExFlags,
                    Py_CompileStringObject,
                    Py_EnterRecursiveCall,
                    Py_GenericAlias,
                    Py_LeaveRecursiveCall,
                    Py_get_PyASCIIObject_length,
                    Py_get_PyASCIIObject_state_ascii,
                    Py_get_PyASCIIObject_state_compact,
                    Py_get_PyASCIIObject_state_kind,
                    Py_get_PyASCIIObject_state_ready,
                    Py_get_PyASCIIObject_wstr,
                    Py_get_PyAsyncMethods_am_aiter,
                    Py_get_PyAsyncMethods_am_anext,
                    Py_get_PyAsyncMethods_am_await,
                    Py_get_PyBufferProcs_bf_getbuffer,
                    Py_get_PyBufferProcs_bf_releasebuffer,
                    Py_get_PyByteArrayObject_ob_exports,
                    Py_get_PyByteArrayObject_ob_start,
                    Py_get_PyCFunctionObject_m_ml,
                    Py_get_PyCFunctionObject_m_module,
                    Py_get_PyCFunctionObject_m_self,
                    Py_get_PyCFunctionObject_m_weakreflist,
                    Py_get_PyCFunctionObject_vectorcall,
                    Py_get_PyCMethodObject_mm_class,
                    Py_get_PyCompactUnicodeObject_wstr_length,
                    Py_get_PyDescrObject_d_name,
                    Py_get_PyDescrObject_d_type,
                    Py_get_PyFrameObject_f_lineno,
                    Py_get_PyGetSetDef_closure,
                    Py_get_PyGetSetDef_doc,
                    Py_get_PyGetSetDef_get,
                    Py_get_PyGetSetDef_name,
                    Py_get_PyGetSetDef_set,
                    Py_get_PyInstanceMethodObject_func,
                    Py_get_PyListObject_ob_item,
                    Py_get_PyLongObject_ob_digit,
                    Py_get_PyMappingMethods_mp_ass_subscript,
                    Py_get_PyMappingMethods_mp_length,
                    Py_get_PyMappingMethods_mp_subscript,
                    Py_get_PyMethodDef_ml_doc,
                    Py_get_PyMethodDef_ml_flags,
                    Py_get_PyMethodDef_ml_meth,
                    Py_get_PyMethodDef_ml_name,
                    Py_get_PyMethodDescrObject_d_method,
                    Py_get_PyMethodObject_im_func,
                    Py_get_PyMethodObject_im_self,
                    Py_get_PyModuleDef_m_doc,
                    Py_get_PyModuleDef_m_methods,
                    Py_get_PyModuleDef_m_name,
                    Py_get_PyModuleDef_m_size,
                    Py_get_PyModuleObject_md_def,
                    Py_get_PyModuleObject_md_dict,
                    Py_get_PyModuleObject_md_state,
                    Py_get_PyNumberMethods_nb_absolute,
                    Py_get_PyNumberMethods_nb_add,
                    Py_get_PyNumberMethods_nb_and,
                    Py_get_PyNumberMethods_nb_bool,
                    Py_get_PyNumberMethods_nb_divmod,
                    Py_get_PyNumberMethods_nb_float,
                    Py_get_PyNumberMethods_nb_floor_divide,
                    Py_get_PyNumberMethods_nb_index,
                    Py_get_PyNumberMethods_nb_inplace_add,
                    Py_get_PyNumberMethods_nb_inplace_and,
                    Py_get_PyNumberMethods_nb_inplace_floor_divide,
                    Py_get_PyNumberMethods_nb_inplace_lshift,
                    Py_get_PyNumberMethods_nb_inplace_matrix_multiply,
                    Py_get_PyNumberMethods_nb_inplace_multiply,
                    Py_get_PyNumberMethods_nb_inplace_or,
                    Py_get_PyNumberMethods_nb_inplace_power,
                    Py_get_PyNumberMethods_nb_inplace_remainder,
                    Py_get_PyNumberMethods_nb_inplace_rshift,
                    Py_get_PyNumberMethods_nb_inplace_subtract,
                    Py_get_PyNumberMethods_nb_inplace_true_divide,
                    Py_get_PyNumberMethods_nb_inplace_xor,
                    Py_get_PyNumberMethods_nb_int,
                    Py_get_PyNumberMethods_nb_invert,
                    Py_get_PyNumberMethods_nb_lshift,
                    Py_get_PyNumberMethods_nb_matrix_multiply,
                    Py_get_PyNumberMethods_nb_multiply,
                    Py_get_PyNumberMethods_nb_negative,
                    Py_get_PyNumberMethods_nb_or,
                    Py_get_PyNumberMethods_nb_positive,
                    Py_get_PyNumberMethods_nb_power,
                    Py_get_PyNumberMethods_nb_remainder,
                    Py_get_PyNumberMethods_nb_rshift,
                    Py_get_PyNumberMethods_nb_subtract,
                    Py_get_PyNumberMethods_nb_true_divide,
                    Py_get_PyNumberMethods_nb_xor,
                    Py_get_PyObject_ob_refcnt,
                    Py_get_PyObject_ob_type,
                    Py_get_PySequenceMethods_sq_ass_item,
                    Py_get_PySequenceMethods_sq_concat,
                    Py_get_PySequenceMethods_sq_contains,
                    Py_get_PySequenceMethods_sq_inplace_concat,
                    Py_get_PySequenceMethods_sq_inplace_repeat,
                    Py_get_PySequenceMethods_sq_item,
                    Py_get_PySequenceMethods_sq_length,
                    Py_get_PySequenceMethods_sq_repeat,
                    Py_get_PySetObject_used,
                    Py_get_PySliceObject_start,
                    Py_get_PySliceObject_step,
                    Py_get_PySliceObject_stop,
                    Py_get_PyThreadState_dict,
                    Py_get_PyTupleObject_ob_item,
                    Py_get_PyTypeObject_tp_alloc,
                    Py_get_PyTypeObject_tp_as_async,
                    Py_get_PyTypeObject_tp_as_buffer,
                    Py_get_PyTypeObject_tp_as_mapping,
                    Py_get_PyTypeObject_tp_as_number,
                    Py_get_PyTypeObject_tp_as_sequence,
                    Py_get_PyTypeObject_tp_base,
                    Py_get_PyTypeObject_tp_bases,
                    Py_get_PyTypeObject_tp_basicsize,
                    Py_get_PyTypeObject_tp_cache,
                    Py_get_PyTypeObject_tp_call,
                    Py_get_PyTypeObject_tp_clear,
                    Py_get_PyTypeObject_tp_dealloc,
                    Py_get_PyTypeObject_tp_del,
                    Py_get_PyTypeObject_tp_descr_get,
                    Py_get_PyTypeObject_tp_descr_set,
                    Py_get_PyTypeObject_tp_dict,
                    Py_get_PyTypeObject_tp_dictoffset,
                    Py_get_PyTypeObject_tp_doc,
                    Py_get_PyTypeObject_tp_finalize,
                    Py_get_PyTypeObject_tp_flags,
                    Py_get_PyTypeObject_tp_free,
                    Py_get_PyTypeObject_tp_getattr,
                    Py_get_PyTypeObject_tp_getattro,
                    Py_get_PyTypeObject_tp_getset,
                    Py_get_PyTypeObject_tp_hash,
                    Py_get_PyTypeObject_tp_init,
                    Py_get_PyTypeObject_tp_is_gc,
                    Py_get_PyTypeObject_tp_itemsize,
                    Py_get_PyTypeObject_tp_iter,
                    Py_get_PyTypeObject_tp_iternext,
                    Py_get_PyTypeObject_tp_members,
                    Py_get_PyTypeObject_tp_methods,
                    Py_get_PyTypeObject_tp_mro,
                    Py_get_PyTypeObject_tp_name,
                    Py_get_PyTypeObject_tp_new,
                    Py_get_PyTypeObject_tp_repr,
                    Py_get_PyTypeObject_tp_richcompare,
                    Py_get_PyTypeObject_tp_setattr,
                    Py_get_PyTypeObject_tp_setattro,
                    Py_get_PyTypeObject_tp_str,
                    Py_get_PyTypeObject_tp_subclasses,
                    Py_get_PyTypeObject_tp_traverse,
                    Py_get_PyTypeObject_tp_vectorcall,
                    Py_get_PyTypeObject_tp_vectorcall_offset,
                    Py_get_PyTypeObject_tp_version_tag,
                    Py_get_PyTypeObject_tp_weaklist,
                    Py_get_PyTypeObject_tp_weaklistoffset,
                    Py_get_PyUnicodeObject_data,
                    Py_get_PyVarObject_ob_size,
                    Py_get_dummy,
                    Py_get_mmap_object_data,
                    Py_set_PyByteArrayObject_ob_exports,
                    Py_set_PyFrameObject_f_lineno,
                    Py_set_PyModuleObject_md_def,
                    Py_set_PyModuleObject_md_state,
                    Py_set_PyObject_ob_refcnt,
                    Py_set_PyTypeObject_tp_alloc,
                    Py_set_PyTypeObject_tp_as_buffer,
                    Py_set_PyTypeObject_tp_base,
                    Py_set_PyTypeObject_tp_bases,
                    Py_set_PyTypeObject_tp_basicsize,
                    Py_set_PyTypeObject_tp_clear,
                    Py_set_PyTypeObject_tp_dealloc,
                    Py_set_PyTypeObject_tp_dict,
                    Py_set_PyTypeObject_tp_dictoffset,
                    Py_set_PyTypeObject_tp_finalize,
                    Py_set_PyTypeObject_tp_flags,
                    Py_set_PyTypeObject_tp_free,
                    Py_set_PyTypeObject_tp_getattr,
                    Py_set_PyTypeObject_tp_getattro,
                    Py_set_PyTypeObject_tp_itemsize,
                    Py_set_PyTypeObject_tp_iter,
                    Py_set_PyTypeObject_tp_iternext,
                    Py_set_PyTypeObject_tp_mro,
                    Py_set_PyTypeObject_tp_new,
                    Py_set_PyTypeObject_tp_setattr,
                    Py_set_PyTypeObject_tp_setattro,
                    Py_set_PyTypeObject_tp_subclasses,
                    Py_set_PyTypeObject_tp_traverse,
                    Py_set_PyTypeObject_tp_vectorcall_offset,
                    Py_set_PyTypeObject_tp_weaklistoffset,
                    Py_set_PyVarObject_ob_size,
                    _PyArray_Resize,
                    _PyBytes_Join,
                    _PyDict_Pop,
                    _PyDict_SetItem_KnownHash,
                    _PyErr_BadInternalCall,
                    _PyErr_WriteUnraisableMsg,
                    _PyList_Extend,
                    _PyList_SET_ITEM,
                    _PyLong_Sign,
                    _PyNamespace_New,
                    _PyNumber_Index,
                    _PyObject_Dump,
                    _PyTraceMalloc_NewReference,
                    _PyTraceback_Add,
                    _PyTruffleBytes_Resize,
                    _PyTruffleErr_CreateAndSetException,
                    _PyTruffleErr_Warn,
                    _PyTruffleEval_EvalCodeEx,
                    _PyTruffleModule_CreateInitialized_PyModule_New,
                    _PyTruffleModule_GetAndIncMaxModuleNumber,
                    _PyTruffleObject_Call1,
                    _PyTruffleObject_CallMethod1,
                    _PyTruffleObject_MakeTpCall,
                    _PyTruffleSet_NextEntry,
                    _PyTruffle_HashBytes,
                    _PyTuple_SET_ITEM,
                    _PyType_Lookup,
                    _PyUnicode_AsASCIIString,
                    _PyUnicode_AsLatin1String,
                    _PyUnicode_AsUTF8String,
                    _PyUnicode_EqualToASCIIString,
                    _Py_GetErrorHandler,
                    _Py_HashDouble,
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
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_Addr2LineNodeGen.create();
            case 18:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_GetFileNameNodeGen.create();
            case 19:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_GetNameNodeGen.create();
            case 20:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_NewNodeGen.create();
            case 21:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_NewEmptyNodeGen.create();
            case 22:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_NewWithPosOnlyArgsNodeGen.create();
            case 23:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodecBuiltinsFactory.PyCodec_DecoderNodeGen.create();
            case 24:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodecBuiltinsFactory.PyCodec_EncoderNodeGen.create();
            case 25:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyComplex_FromDoublesNodeGen.create();
            case 26:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyComplex_ImagAsDoubleNodeGen.create();
            case 27:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyComplex_RealAsDoubleNodeGen.create();
            case 28:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextContextBuiltinsFactory.PyContextVar_NewNodeGen.create();
            case 29:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextContextBuiltinsFactory.PyContextVar_SetNodeGen.create();
            case 30:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsFactory.PyDictProxy_NewNodeGen.create();
            case 31:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_ClearNodeGen.create();
            case 32:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_ContainsNodeGen.create();
            case 33:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_CopyNodeGen.create();
            case 34:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_DelItemNodeGen.create();
            case 35:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_GetItemNodeGen.create();
            case 36:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_GetItemWithErrorNodeGen.create();
            case 37:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_ItemsNodeGen.create();
            case 38:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_KeysNodeGen.create();
            case 39:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_MergeNodeGen.create();
            case 40:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_NewNodeGen.create();
            case 41:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_SetDefaultNodeGen.create();
            case 42:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_SetItemNodeGen.create();
            case 43:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_SizeNodeGen.create();
            case 44:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_UpdateNodeGen.create();
            case 45:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_ValuesNodeGen.create();
            case 46:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_DisplayNodeGen.create();
            case 47:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_GivenExceptionMatchesNodeGen.create();
            case 48:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_NewExceptionNodeGen.create();
            case 49:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_NewExceptionWithDocNodeGen.create();
            case 50:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_OccurredNodeGen.create();
            case 51:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_PrintExNodeGen.create();
            case 52:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_RestoreNodeGen.create();
            case 53:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_SetExcInfoNodeGen.create();
            case 54:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyEval_GetBuiltinsNodeGen.create();
            case 55:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyEval_GetFrameNodeGen.create();
            case 56:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyEval_RestoreThreadNodeGen.create();
            case 57:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyEval_SaveThreadNodeGen.create();
            case 58:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_GetCauseNodeGen.create();
            case 59:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_GetContextNodeGen.create();
            case 60:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_SetCauseNodeGen.create();
            case 61:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_SetContextNodeGen.create();
            case 62:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_SetTracebackNodeGen.create();
            case 63:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFileBuiltinsFactory.PyFile_WriteObjectNodeGen.create();
            case 64:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFloatBuiltinsFactory.PyFloat_FromDoubleNodeGen.create();
            case 65:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFloatBuiltinsFactory.PyFloat_FromStringNodeGen.create();
            case 66:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFrameBuiltinsFactory.PyFrame_GetBackNodeGen.create();
            case 67:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFrameBuiltinsFactory.PyFrame_GetBuiltinsNodeGen.create();
            case 68:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFrameBuiltinsFactory.PyFrame_GetCodeNodeGen.create();
            case 69:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFrameBuiltinsFactory.PyFrame_GetGlobalsNodeGen.create();
            case 70:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFrameBuiltinsFactory.PyFrame_GetLastiNodeGen.create();
            case 71:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFrameBuiltinsFactory.PyFrame_GetLineNumberNodeGen.create();
            case 72:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFrameBuiltinsFactory.PyFrame_GetLocalsNodeGen.create();
            case 73:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyFrame_NewNodeGen.create();
            case 74:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PyFrozenSet_NewNodeGen.create();
            case 75:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyGILState_CheckNodeGen.create();
            case 76:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_GetModuleDictNodeGen.create();
            case 77:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleNodeGen.create();
            case 78:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleNodeGen.create();
            case 79:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleLevelObjectNodeGen.create();
            case 80:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleNodeGen.create();
            case 81:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyIndex_CheckNodeGen.create();
            case 82:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextClassBuiltinsFactory.PyInstanceMethod_NewNodeGen.create();
            case 83:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextIterBuiltinsFactory.PyIter_CheckNodeGen.create();
            case 84:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyIter_NextNodeGen.create();
            case 85:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_AppendNodeGen.create();
            case 86:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_AsTupleNodeGen.create();
            case 87:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_GetItemNodeGen.create();
            case 88:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_GetSliceNodeGen.create();
            case 89:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_InsertNodeGen.create();
            case 90:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_NewNodeGen.create();
            case 91:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_ReverseNodeGen.create();
            case 92:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SetItemNodeGen.create();
            case 93:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SetSliceNodeGen.create();
            case 94:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SizeNodeGen.create();
            case 95:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SortNodeGen.create();
            case 96:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_AsVoidPtrNodeGen.create();
            case 97:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromDoubleNodeGen.create();
            case 98:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongNodeGen.create();
            case 99:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongNodeGen.create();
            case 100:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromUnsignedLongLongNodeGen.create();
            case 101:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongNodeGen.create();
            case 102:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromUnsignedLongLongNodeGen.create();
            case 103:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromUnsignedLongLongNodeGen.create();
            case 104:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_CheckNodeGen.create();
            case 105:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_ItemsNodeGen.create();
            case 106:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_KeysNodeGen.create();
            case 107:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_SizeNodeGen.create();
            case 108:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_ValuesNodeGen.create();
            case 109:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextMemoryViewBuiltinsFactory.PyMemoryView_FromObjectNodeGen.create();
            case 110:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextMemoryViewBuiltinsFactory.PyMemoryView_GetContiguousNodeGen.create();
            case 111:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextClassBuiltinsFactory.PyMethod_NewNodeGen.create();
            case 112:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_AddIntConstantNodeGen.create();
            case 113:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_AddObjectRefNodeGen.create();
            case 114:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_GetNameObjectNodeGen.create();
            case 115:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_NewObjectNodeGen.create();
            case 116:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_NewObjectNodeGen.create();
            case 117:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_SetDocStringNodeGen.create();
            case 118:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_AbsoluteNodeGen.create();
            case 119:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_CheckNodeGen.create();
            case 120:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_DivmodNodeGen.create();
            case 121:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_FloatNodeGen.create();
            case 122:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_InPlacePowerNodeGen.create();
            case 123:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_IndexNodeGen.create();
            case 124:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_LongNodeGen.create();
            case 125:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_PowerNodeGen.create();
            case 126:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_ToBaseNodeGen.create();
            case 127:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPosixmoduleBuiltinsFactory.PyOS_FSPathNodeGen.create();
            case 128:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_ASCIINodeGen.create();
            case 129:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_AsFileDescriptorNodeGen.create();
            case 130:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_BytesNodeGen.create();
            case 131:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWeakrefBuiltinsFactory.PyObject_ClearWeakRefsNodeGen.create();
            case 132:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_DelItemNodeGen.create();
            case 133:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_DirNodeGen.create();
            case 134:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_FormatNodeGen.create();
            case 135:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyObject_GC_TrackNodeGen.create();
            case 136:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyObject_GC_UnTrackNodeGen.create();
            case 137:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_GetDocNodeGen.create();
            case 138:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_GetItemNodeGen.create();
            case 139:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_GetIterNodeGen.create();
            case 140:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HasAttrNodeGen.create();
            case 141:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HasAttrNodeGen.create();
            case 142:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HashNodeGen.create();
            case 143:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HashNotImplementedNodeGen.create();
            case 144:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_IsInstanceNodeGen.create();
            case 145:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_IsSubclassNodeGen.create();
            case 146:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_IsTrueNodeGen.create();
            case 147:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_LengthHintNodeGen.create();
            case 148:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_ReprNodeGen.create();
            case 149:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_RichCompareNodeGen.create();
            case 150:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_SetDocNodeGen.create();
            case 151:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_SetItemNodeGen.create();
            case 152:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_SizeNodeGen.create();
            case 153:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_StrNodeGen.create();
            case 154:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_TypeNodeGen.create();
            case 155:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPythonRunBuiltinsFactory.PyRun_StringFlagsNodeGen.create();
            case 156:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextIterBuiltinsFactory.PySeqIter_NewNodeGen.create();
            case 157:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_CheckNodeGen.create();
            case 158:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_ConcatNodeGen.create();
            case 159:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_ContainsNodeGen.create();
            case 160:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_CountNodeGen.create();
            case 161:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_DelItemNodeGen.create();
            case 162:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_DelSliceNodeGen.create();
            case 163:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_GetItemNodeGen.create();
            case 164:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_GetSliceNodeGen.create();
            case 165:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_InPlaceConcatNodeGen.create();
            case 166:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_InPlaceRepeatNodeGen.create();
            case 167:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_IndexNodeGen.create();
            case 168:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_SizeNodeGen.create();
            case 169:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_ListNodeGen.create();
            case 170:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_RepeatNodeGen.create();
            case 171:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_SetItemNodeGen.create();
            case 172:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_SetSliceNodeGen.create();
            case 173:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_SizeNodeGen.create();
            case 174:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_TupleNodeGen.create();
            case 175:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_AddNodeGen.create();
            case 176:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_ClearNodeGen.create();
            case 177:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_ContainsNodeGen.create();
            case 178:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_DiscardNodeGen.create();
            case 179:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_NewNodeGen.create();
            case 180:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_PopNodeGen.create();
            case 181:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_SizeNodeGen.create();
            case 182:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSliceBuiltinsFactory.PySlice_NewNodeGen.create();
            case 183:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFuncBuiltinsFactory.PyStaticMethod_NewNodeGen.create();
            case 184:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextStructSeqBuiltinsFactory.PyStructSequence_NewNodeGen.create();
            case 185:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSysBuiltinsFactory.PySys_GetObjectNodeGen.create();
            case 186:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyThreadState_GetNodeGen.create();
            case 187:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyThreadState_GetDictNodeGen.create();
            case 188:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyThreadBuiltinsFactory.PyThread_acquire_lockNodeGen.create();
            case 189:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyThreadBuiltinsFactory.PyThread_allocate_lockNodeGen.create();
            case 190:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyThreadBuiltinsFactory.PyThread_get_thread_identNodeGen.create();
            case 191:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyThreadBuiltinsFactory.PyThread_release_lockNodeGen.create();
            case 192:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTracebackBuiltinsFactory.PyTraceBack_HereNodeGen.create();
            case 193:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTraceMalloc_TrackNodeGen.create();
            case 194:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTraceMalloc_UntrackNodeGen.create();
            case 195:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleByteArray_FromStringAndSizeNodeGen.create();
            case 196:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleBytes_ConcatNodeGen.create();
            case 197:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleBytes_FromFormatNodeGen.create();
            case 198:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleBytes_FromStringAndSizeNodeGen.create();
            case 199:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextMethodBuiltinsFactory.PyTruffleCMethod_NewExNodeGen.create();
            case 200:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyTruffleComplex_AsCComplexNodeGen.create();
            case 201:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextContextBuiltinsFactory.PyTruffleContextVar_GetNodeGen.create();
            case 202:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDateTimeBuiltinsFactory.PyTruffleDateTimeCAPI_DateTime_FromDateAndTimeNodeGen.create();
            case 203:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDateTimeBuiltinsFactory.PyTruffleDateTimeCAPI_DateTime_FromDateAndTimeAndFoldNodeGen.create();
            case 204:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDateTimeBuiltinsFactory.PyTruffleDateTimeCAPI_DateTime_FromTimestampNodeGen.create();
            case 205:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDateTimeBuiltinsFactory.PyTruffleDateTimeCAPI_Date_FromDateNodeGen.create();
            case 206:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDateTimeBuiltinsFactory.PyTruffleDateTimeCAPI_Date_FromTimestampNodeGen.create();
            case 207:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDateTimeBuiltinsFactory.PyTruffleDateTimeCAPI_Delta_FromDeltaNodeGen.create();
            case 208:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDateTimeBuiltinsFactory.PyTruffleDateTimeCAPI_TimeZone_FromTimeZoneNodeGen.create();
            case 209:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDateTimeBuiltinsFactory.PyTruffleDateTimeCAPI_Time_FromTimeNodeGen.create();
            case 210:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDateTimeBuiltinsFactory.PyTruffleDateTimeCAPI_Time_FromTimeAndFoldNodeGen.create();
            case 211:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsFactory.PyTruffleDescr_NewClassMethodNodeGen.create();
            case 212:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsFactory.PyTruffleDescr_NewGetSetNodeGen.create();
            case 213:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyTruffleDict_NextNodeGen.create();
            case 214:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyTruffleErr_FetchNodeGen.create();
            case 215:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyTruffleErr_GetExcInfoNodeGen.create();
            case 216:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWarnBuiltinsFactory.PyTruffleErr_WarnExplicitNodeGen.create();
            case 217:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFloatBuiltinsFactory.PyTruffleFloat_AsDoubleNodeGen.create();
            case 218:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyTruffleGILState_EnsureNodeGen.create();
            case 219:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyTruffleGILState_ReleaseNodeGen.create();
            case 220:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory.PyTruffleHash_InitSecretNodeGen.create();
            case 221:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_AsPrimitiveNodeGen.create();
            case 222:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_FromStringNodeGen.create();
            case 223:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_OneNodeGen.create();
            case 224:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_ZeroNodeGen.create();
            case 225:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyTruffleModule_AddFunctionToModuleNodeGen.create();
            case 226:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyTruffleNumber_BinOpNodeGen.create();
            case 227:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyTruffleNumber_InPlaceBinOpNodeGen.create();
            case 228:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyTruffleNumber_UnaryOpNodeGen.create();
            case 229:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_CallFunctionObjArgsNodeGen.create();
            case 230:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_CallMethodObjArgsNodeGen.create();
            case 231:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_GenericGetAttrNodeGen.create();
            case 232:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_GenericSetAttrNodeGen.create();
            case 233:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_GetItemNodeGen.create();
            case 234:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyTruffleState_FindModuleNodeGen.create();
            case 235:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextStructSeqBuiltinsFactory.PyTruffleStructSequence_InitType2NodeGen.create();
            case 236:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextStructSeqBuiltinsFactory.PyTruffleStructSequence_NewTypeNodeGen.create();
            case 237:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleToCharPointerNodeGen.create();
            case 238:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyTruffleType_AddFunctionToTypeNodeGen.create();
            case 239:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyTruffleType_AddGetSetNodeGen.create();
            case 240:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyTruffleType_AddMemberNodeGen.create();
            case 241:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyTruffleType_AddSlotNodeGen.create();
            case 242:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_DecodeNodeGen.create();
            case 243:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_DecodeUTF8StatefulNodeGen.create();
            case 244:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_FromUCSNodeGen.create();
            case 245:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_InternInPlaceNodeGen.create();
            case 246:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_NewNodeGen.create();
            case 247:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Arg_ParseTupleAndKeywordsNodeGen.create();
            case 248:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffle_ByteArray_EmptyWithCapacityNodeGen.create();
            case 249:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffle_Bytes_AsStringNodeGen.create();
            case 250:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffle_Bytes_CheckEmbeddedNullNodeGen.create();
            case 251:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffle_Bytes_EmptyWithCapacityNodeGen.create();
            case 252:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyTruffle_Compute_MroNodeGen.create();
            case 253:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_DebugNodeGen.create();
            case 254:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_DebugTraceNodeGen.create();
            case 255:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSliceBuiltinsFactory.PyTruffle_EllipsisNodeGen.create();
            case 256:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBoolBuiltinsFactory.PyTruffle_FalseNodeGen.create();
            case 257:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyLifecycleBuiltinsFactory.PyTruffle_FatalErrorFuncNodeGen.create();
            case 258:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_FileSystemDefaultEncodingNodeGen.create();
            case 259:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Get_Inherited_Native_SlotsNodeGen.create();
            case 260:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory.PyTruffle_HashConstantNodeGen.create();
            case 261:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_InitialNativeMemoryNodeGen.create();
            case 262:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_LogStringNodeGen.create();
            case 263:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_MaxNativeMemoryNodeGen.create();
            case 264:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_MemoryViewFromBufferNodeGen.create();
            case 265:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Native_OptionsNodeGen.create();
            case 266:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyTruffle_NewTypeDictNodeGen.create();
            case 267:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffle_NoValueNodeGen.create();
            case 268:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffle_NoneNodeGen.create();
            case 269:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffle_NotImplementedNodeGen.create();
            case 270:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Object_FreeNodeGen.create();
            case 271:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDateTimeBuiltinsFactory.PyTruffle_PyDateTime_GET_TZINFONodeGen.create();
            case 272:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Register_NULLNodeGen.create();
            case 273:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Set_Native_SlotsNodeGen.create();
            case 274:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Set_SulongTypeNodeGen.create();
            case 275:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_ToNativeNodeGen.create();
            case 276:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyTruffle_Trace_TypeNodeGen.create();
            case 277:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_TriggerGCNodeGen.create();
            case 278:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBoolBuiltinsFactory.PyTruffle_TrueNodeGen.create();
            case 279:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_TypeNodeGen.create();
            case 280:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyTruffle_Type_ModifiedNodeGen.create();
            case 281:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_AsUTF8AndSize_CharPtrNodeGen.create();
            case 282:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_AsUTF8AndSize_SizeNodeGen.create();
            case 283:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_AsUnicodeAndSize_CharPtrNodeGen.create();
            case 284:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_AsUnicodeAndSize_SizeNodeGen.create();
            case 285:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_AsWideCharNodeGen.create();
            case 286:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_DecodeUTF32NodeGen.create();
            case 287:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_FromFormatNodeGen.create();
            case 288:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_FromWcharNodeGen.create();
            case 289:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyThreadBuiltinsFactory.PyTruffle_tss_createNodeGen.create();
            case 290:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyThreadBuiltinsFactory.PyTruffle_tss_deleteNodeGen.create();
            case 291:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyThreadBuiltinsFactory.PyTruffle_tss_getNodeGen.create();
            case 292:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyThreadBuiltinsFactory.PyTruffle_tss_setNodeGen.create();
            case 293:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_GetItemNodeGen.create();
            case 294:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_GetSliceNodeGen.create();
            case 295:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_NewNodeGen.create();
            case 296:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_SetItemNodeGen.create();
            case 297:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_SizeNodeGen.create();
            case 298:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyType_IsSubtypeNodeGen.create();
            case 299:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicodeDecodeError_CreateNodeGen.create();
            case 300:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_AsEncodedStringNodeGen.create();
            case 301:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_AsUnicodeEscapeStringNodeGen.create();
            case 302:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_CompareNodeGen.create();
            case 303:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ConcatNodeGen.create();
            case 304:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ContainsNodeGen.create();
            case 305:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_DecodeFSDefaultNodeGen.create();
            case 306:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_EncodeFSDefaultNodeGen.create();
            case 307:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FindCharNodeGen.create();
            case 308:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FormatNodeGen.create();
            case 309:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromEncodedObjectNodeGen.create();
            case 310:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromObjectNodeGen.create();
            case 311:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromOrdinalNodeGen.create();
            case 312:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromStringNodeGen.create();
            case 313:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_JoinNodeGen.create();
            case 314:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ReadCharNodeGen.create();
            case 315:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ReplaceNodeGen.create();
            case 316:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_SplitNodeGen.create();
            case 317:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_SubstringNodeGen.create();
            case 318:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_TailmatchNodeGen.create();
            case 319:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWeakrefBuiltinsFactory.PyWeakref_GetObjectNodeGen.create();
            case 320:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWeakrefBuiltinsFactory.PyWeakref_NewRefNodeGen.create();
            case 321:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyLifecycleBuiltinsFactory.Py_AtExitNodeGen.create();
            case 322:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPythonRunBuiltinsFactory.Py_CompileStringNodeGen.create();
            case 323:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPythonRunBuiltinsFactory.Py_CompileStringExFlagsNodeGen.create();
            case 324:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPythonRunBuiltinsFactory.Py_CompileStringObjectNodeGen.create();
            case 325:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.Py_EnterRecursiveCallNodeGen.create();
            case 326:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextGenericAliasBuiltinsFactory.Py_GenericAliasNodeGen.create();
            case 327:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.Py_LeaveRecursiveCallNodeGen.create();
            case 328:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_lengthNodeGen.create();
            case 329:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_asciiNodeGen.create();
            case 330:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_compactNodeGen.create();
            case 331:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_kindNodeGen.create();
            case 332:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_readyNodeGen.create();
            case 333:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_wstrNodeGen.create();
            case 334:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 335:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 336:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 337:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 338:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 339:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyByteArrayObject_ob_exportsNodeGen.create();
            case 340:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyByteArrayObject_ob_startNodeGen.create();
            case 341:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_mlNodeGen.create();
            case 342:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_moduleNodeGen.create();
            case 343:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_selfNodeGen.create();
            case 344:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_weakreflistNodeGen.create();
            case 345:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_vectorcallNodeGen.create();
            case 346:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCMethodObject_mm_classNodeGen.create();
            case 347:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCompactUnicodeObject_wstr_lengthNodeGen.create();
            case 348:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyDescrObject_d_nameNodeGen.create();
            case 349:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyDescrObject_d_typeNodeGen.create();
            case 350:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyFrameObject_f_linenoNodeGen.create();
            case 351:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_closureNodeGen.create();
            case 352:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_docNodeGen.create();
            case 353:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_getNodeGen.create();
            case 354:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_nameNodeGen.create();
            case 355:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_setNodeGen.create();
            case 356:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyInstanceMethodObject_funcNodeGen.create();
            case 357:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PSequence_ob_itemNodeGen.create();
            case 358:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyLongObject_ob_digitNodeGen.create();
            case 359:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMappingMethods_mp_ass_subscriptNodeGen.create();
            case 360:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMappingMethods_mp_lengthNodeGen.create();
            case 361:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMappingMethods_mp_subscriptNodeGen.create();
            case 362:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_docNodeGen.create();
            case 363:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_flagsNodeGen.create();
            case 364:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_methNodeGen.create();
            case 365:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_nameNodeGen.create();
            case 366:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDescrObject_d_methodNodeGen.create();
            case 367:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodObject_im_funcNodeGen.create();
            case 368:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodObject_im_selfNodeGen.create();
            case 369:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_docNodeGen.create();
            case 370:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_methodsNodeGen.create();
            case 371:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_nameNodeGen.create();
            case 372:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_sizeNodeGen.create();
            case 373:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleObject_md_defNodeGen.create();
            case 374:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleObject_md_dictNodeGen.create();
            case 375:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleObject_md_stateNodeGen.create();
            case 376:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_absoluteNodeGen.create();
            case 377:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_addNodeGen.create();
            case 378:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_andNodeGen.create();
            case 379:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_boolNodeGen.create();
            case 380:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_divmodNodeGen.create();
            case 381:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_floatNodeGen.create();
            case 382:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_floor_divideNodeGen.create();
            case 383:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_indexNodeGen.create();
            case 384:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_addNodeGen.create();
            case 385:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_andNodeGen.create();
            case 386:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_floor_divideNodeGen.create();
            case 387:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_lshiftNodeGen.create();
            case 388:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 389:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_multiplyNodeGen.create();
            case 390:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_orNodeGen.create();
            case 391:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_powerNodeGen.create();
            case 392:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_remainderNodeGen.create();
            case 393:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_rshiftNodeGen.create();
            case 394:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_subtractNodeGen.create();
            case 395:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_true_divideNodeGen.create();
            case 396:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_xorNodeGen.create();
            case 397:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_intNodeGen.create();
            case 398:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_invertNodeGen.create();
            case 399:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_lshiftNodeGen.create();
            case 400:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 401:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_multiplyNodeGen.create();
            case 402:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_negativeNodeGen.create();
            case 403:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_orNodeGen.create();
            case 404:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_positiveNodeGen.create();
            case 405:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_powerNodeGen.create();
            case 406:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_remainderNodeGen.create();
            case 407:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_rshiftNodeGen.create();
            case 408:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_subtractNodeGen.create();
            case 409:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_true_divideNodeGen.create();
            case 410:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_xorNodeGen.create();
            case 411:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyObject_ob_refcntNodeGen.create();
            case 412:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyObject_ob_typeNodeGen.create();
            case 413:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 414:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySequenceMethods_sq_concatNodeGen.create();
            case 415:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 416:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 417:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 418:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySequenceMethods_sq_itemNodeGen.create();
            case 419:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySequenceMethods_sq_lengthNodeGen.create();
            case 420:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySequenceMethods_sq_repeatNodeGen.create();
            case 421:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySetObject_usedNodeGen.create();
            case 422:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySliceObject_startNodeGen.create();
            case 423:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySliceObject_stepNodeGen.create();
            case 424:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySliceObject_stopNodeGen.create();
            case 425:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyThreadState_dictNodeGen.create();
            case 426:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PSequence_ob_itemNodeGen.create();
            case 427:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_allocNodeGen.create();
            case 428:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_asyncNodeGen.create();
            case 429:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_bufferNodeGen.create();
            case 430:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_mappingNodeGen.create();
            case 431:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_numberNodeGen.create();
            case 432:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_sequenceNodeGen.create();
            case 433:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_baseNodeGen.create();
            case 434:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_basesNodeGen.create();
            case 435:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_basicsizeNodeGen.create();
            case 436:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPyPtrNodeGen.create();
            case 437:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_callNodeGen.create();
            case 438:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_TraverseClearNodeGen.create();
            case 439:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_deallocNodeGen.create();
            case 440:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_delNodeGen.create();
            case 441:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_descr_getNodeGen.create();
            case 442:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_descr_setNodeGen.create();
            case 443:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_dictNodeGen.create();
            case 444:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_dictoffsetNodeGen.create();
            case 445:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_docNodeGen.create();
            case 446:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 447:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_flagsNodeGen.create();
            case 448:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_freeNodeGen.create();
            case 449:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 450:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_getattroNodeGen.create();
            case 451:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 452:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_hashNodeGen.create();
            case 453:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_initNodeGen.create();
            case 454:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 455:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_itemsizeNodeGen.create();
            case 456:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_iterNodeGen.create();
            case 457:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_iternextNodeGen.create();
            case 458:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 459:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 460:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_mroNodeGen.create();
            case 461:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_nameNodeGen.create();
            case 462:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_newNodeGen.create();
            case 463:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_reprNodeGen.create();
            case 464:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_richcompareNodeGen.create();
            case 465:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 466:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_setattroNodeGen.create();
            case 467:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_strNodeGen.create();
            case 468:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_subclassesNodeGen.create();
            case 469:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_TraverseClearNodeGen.create();
            case 470:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 471:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_vectorcall_offsetNodeGen.create();
            case 472:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_version_tagNodeGen.create();
            case 473:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPyPtrNodeGen.create();
            case 474:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_weaklistoffsetNodeGen.create();
            case 475:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyUnicodeObject_dataNodeGen.create();
            case 476:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyVarObject_ob_sizeNodeGen.create();
            case 477:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrNodeGen.create();
            case 478:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_mmap_object_dataNodeGen.create();
            case 479:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyByteArrayObject_ob_exportsNodeGen.create();
            case 480:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyFrameObject_f_linenoNodeGen.create();
            case 481:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyModuleObject_md_defNodeGen.create();
            case 482:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyModuleObject_md_stateNodeGen.create();
            case 483:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyObject_ob_refcntNodeGen.create();
            case 484:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_allocNodeGen.create();
            case 485:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_as_bufferNodeGen.create();
            case 486:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 487:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 488:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_basicsizeNodeGen.create();
            case 489:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 490:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_deallocNodeGen.create();
            case 491:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_dictNodeGen.create();
            case 492:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_dictoffsetNodeGen.create();
            case 493:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 494:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_flagsNodeGen.create();
            case 495:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_freeNodeGen.create();
            case 496:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 497:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 498:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_itemsizeNodeGen.create();
            case 499:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 500:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 501:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 502:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 503:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 504:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 505:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_subclassesNodeGen.create();
            case 506:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 507:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_vectorcall_offsetNodeGen.create();
            case 508:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 509:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrNodeGen.create();
            case 510:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextArrayBuiltinsFactory._PyArray_ResizeNodeGen.create();
            case 511:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory._PyBytes_JoinNodeGen.create();
            case 512:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory._PyDict_PopNodeGen.create();
            case 513:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory._PyDict_SetItem_KnownHashNodeGen.create();
            case 514:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory._PyErr_BadInternalCallNodeGen.create();
            case 515:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory._PyErr_WriteUnraisableMsgNodeGen.create();
            case 516:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory._PyList_ExtendNodeGen.create();
            case 517:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory._PyList_SET_ITEMNodeGen.create();
            case 518:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory._PyLong_SignNodeGen.create();
            case 519:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextNamespaceBuiltinsFactory._PyNamespace_NewNodeGen.create();
            case 520:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_IndexNodeGen.create();
            case 521:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyObject_DumpNodeGen.create();
            case 522:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory._PyTraceMalloc_NewReferenceNodeGen.create();
            case 523:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTracebackBuiltinsFactory._PyTraceback_AddNodeGen.create();
            case 524:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory._PyTruffleBytes_ResizeNodeGen.create();
            case 525:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory._PyTruffleErr_CreateAndSetExceptionNodeGen.create();
            case 526:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWarnBuiltinsFactory._PyTruffleErr_WarnNodeGen.create();
            case 527:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory._PyTruffleEval_EvalCodeExNodeGen.create();
            case 528:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory._PyTruffleModule_CreateInitialized_PyModule_NewNodeGen.create();
            case 529:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory._PyTruffleModule_GetAndIncMaxModuleNumberNodeGen.create();
            case 530:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyTruffleObject_Call1NodeGen.create();
            case 531:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyTruffleObject_CallMethod1NodeGen.create();
            case 532:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyTruffleObject_MakeTpCallNodeGen.create();
            case 533:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory._PyTruffleSet_NextEntryNodeGen.create();
            case 534:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory._PyTruffle_HashBytesNodeGen.create();
            case 535:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory._PyTuple_SET_ITEMNodeGen.create();
            case 536:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory._PyType_LookupNodeGen.create();
            case 537:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._PyUnicode_AsASCIIStringNodeGen.create();
            case 538:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._PyUnicode_AsLatin1StringNodeGen.create();
            case 539:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._PyUnicode_AsUTF8StringNodeGen.create();
            case 540:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._PyUnicode_EqualToASCIIStringNodeGen.create();
            case 541:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._Py_GetErrorHandlerNodeGen.create();
            case 542:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory._Py_HashDoubleNodeGen.create();
        }
        return null;
    }

    public static CApiBuiltinExecutable getSlot(String key) {
        switch (key) {
            case "PyASCIIObject_length":
                return builtins[328];
            case "PyASCIIObject_state_ascii":
                return builtins[329];
            case "PyASCIIObject_state_compact":
                return builtins[330];
            case "PyASCIIObject_state_kind":
                return builtins[331];
            case "PyASCIIObject_state_ready":
                return builtins[332];
            case "PyASCIIObject_wstr":
                return builtins[333];
            case "PyAsyncMethods_am_aiter":
                return builtins[334];
            case "PyAsyncMethods_am_anext":
                return builtins[335];
            case "PyAsyncMethods_am_await":
                return builtins[336];
            case "PyBufferProcs_bf_getbuffer":
                return builtins[337];
            case "PyBufferProcs_bf_releasebuffer":
                return builtins[338];
            case "PyByteArrayObject_ob_exports":
                return builtins[339];
            case "PyByteArrayObject_ob_start":
                return builtins[340];
            case "PyCFunctionObject_m_ml":
                return builtins[341];
            case "PyCFunctionObject_m_module":
                return builtins[342];
            case "PyCFunctionObject_m_self":
                return builtins[343];
            case "PyCFunctionObject_m_weakreflist":
                return builtins[344];
            case "PyCFunctionObject_vectorcall":
                return builtins[345];
            case "PyCMethodObject_mm_class":
                return builtins[346];
            case "PyCompactUnicodeObject_wstr_length":
                return builtins[347];
            case "PyDescrObject_d_name":
                return builtins[348];
            case "PyDescrObject_d_type":
                return builtins[349];
            case "PyFrameObject_f_lineno":
                return builtins[350];
            case "PyGetSetDef_closure":
                return builtins[351];
            case "PyGetSetDef_doc":
                return builtins[352];
            case "PyGetSetDef_get":
                return builtins[353];
            case "PyGetSetDef_name":
                return builtins[354];
            case "PyGetSetDef_set":
                return builtins[355];
            case "PyInstanceMethodObject_func":
                return builtins[356];
            case "PyListObject_ob_item":
                return builtins[357];
            case "PyLongObject_ob_digit":
                return builtins[358];
            case "PyMappingMethods_mp_ass_subscript":
                return builtins[359];
            case "PyMappingMethods_mp_length":
                return builtins[360];
            case "PyMappingMethods_mp_subscript":
                return builtins[361];
            case "PyMethodDef_ml_doc":
                return builtins[362];
            case "PyMethodDef_ml_flags":
                return builtins[363];
            case "PyMethodDef_ml_meth":
                return builtins[364];
            case "PyMethodDef_ml_name":
                return builtins[365];
            case "PyMethodDescrObject_d_method":
                return builtins[366];
            case "PyMethodObject_im_func":
                return builtins[367];
            case "PyMethodObject_im_self":
                return builtins[368];
            case "PyModuleDef_m_doc":
                return builtins[369];
            case "PyModuleDef_m_methods":
                return builtins[370];
            case "PyModuleDef_m_name":
                return builtins[371];
            case "PyModuleDef_m_size":
                return builtins[372];
            case "PyModuleObject_md_def":
                return builtins[373];
            case "PyModuleObject_md_dict":
                return builtins[374];
            case "PyModuleObject_md_state":
                return builtins[375];
            case "PyNumberMethods_nb_absolute":
                return builtins[376];
            case "PyNumberMethods_nb_add":
                return builtins[377];
            case "PyNumberMethods_nb_and":
                return builtins[378];
            case "PyNumberMethods_nb_bool":
                return builtins[379];
            case "PyNumberMethods_nb_divmod":
                return builtins[380];
            case "PyNumberMethods_nb_float":
                return builtins[381];
            case "PyNumberMethods_nb_floor_divide":
                return builtins[382];
            case "PyNumberMethods_nb_index":
                return builtins[383];
            case "PyNumberMethods_nb_inplace_add":
                return builtins[384];
            case "PyNumberMethods_nb_inplace_and":
                return builtins[385];
            case "PyNumberMethods_nb_inplace_floor_divide":
                return builtins[386];
            case "PyNumberMethods_nb_inplace_lshift":
                return builtins[387];
            case "PyNumberMethods_nb_inplace_matrix_multiply":
                return builtins[388];
            case "PyNumberMethods_nb_inplace_multiply":
                return builtins[389];
            case "PyNumberMethods_nb_inplace_or":
                return builtins[390];
            case "PyNumberMethods_nb_inplace_power":
                return builtins[391];
            case "PyNumberMethods_nb_inplace_remainder":
                return builtins[392];
            case "PyNumberMethods_nb_inplace_rshift":
                return builtins[393];
            case "PyNumberMethods_nb_inplace_subtract":
                return builtins[394];
            case "PyNumberMethods_nb_inplace_true_divide":
                return builtins[395];
            case "PyNumberMethods_nb_inplace_xor":
                return builtins[396];
            case "PyNumberMethods_nb_int":
                return builtins[397];
            case "PyNumberMethods_nb_invert":
                return builtins[398];
            case "PyNumberMethods_nb_lshift":
                return builtins[399];
            case "PyNumberMethods_nb_matrix_multiply":
                return builtins[400];
            case "PyNumberMethods_nb_multiply":
                return builtins[401];
            case "PyNumberMethods_nb_negative":
                return builtins[402];
            case "PyNumberMethods_nb_or":
                return builtins[403];
            case "PyNumberMethods_nb_positive":
                return builtins[404];
            case "PyNumberMethods_nb_power":
                return builtins[405];
            case "PyNumberMethods_nb_remainder":
                return builtins[406];
            case "PyNumberMethods_nb_rshift":
                return builtins[407];
            case "PyNumberMethods_nb_subtract":
                return builtins[408];
            case "PyNumberMethods_nb_true_divide":
                return builtins[409];
            case "PyNumberMethods_nb_xor":
                return builtins[410];
            case "PyObject_ob_refcnt":
                return builtins[411];
            case "PyObject_ob_type":
                return builtins[412];
            case "PySequenceMethods_sq_ass_item":
                return builtins[413];
            case "PySequenceMethods_sq_concat":
                return builtins[414];
            case "PySequenceMethods_sq_contains":
                return builtins[415];
            case "PySequenceMethods_sq_inplace_concat":
                return builtins[416];
            case "PySequenceMethods_sq_inplace_repeat":
                return builtins[417];
            case "PySequenceMethods_sq_item":
                return builtins[418];
            case "PySequenceMethods_sq_length":
                return builtins[419];
            case "PySequenceMethods_sq_repeat":
                return builtins[420];
            case "PySetObject_used":
                return builtins[421];
            case "PySliceObject_start":
                return builtins[422];
            case "PySliceObject_step":
                return builtins[423];
            case "PySliceObject_stop":
                return builtins[424];
            case "PyThreadState_dict":
                return builtins[425];
            case "PyTupleObject_ob_item":
                return builtins[426];
            case "PyTypeObject_tp_alloc":
                return builtins[427];
            case "PyTypeObject_tp_as_async":
                return builtins[428];
            case "PyTypeObject_tp_as_buffer":
                return builtins[429];
            case "PyTypeObject_tp_as_mapping":
                return builtins[430];
            case "PyTypeObject_tp_as_number":
                return builtins[431];
            case "PyTypeObject_tp_as_sequence":
                return builtins[432];
            case "PyTypeObject_tp_base":
                return builtins[433];
            case "PyTypeObject_tp_bases":
                return builtins[434];
            case "PyTypeObject_tp_basicsize":
                return builtins[435];
            case "PyTypeObject_tp_cache":
                return builtins[436];
            case "PyTypeObject_tp_call":
                return builtins[437];
            case "PyTypeObject_tp_clear":
                return builtins[438];
            case "PyTypeObject_tp_dealloc":
                return builtins[439];
            case "PyTypeObject_tp_del":
                return builtins[440];
            case "PyTypeObject_tp_descr_get":
                return builtins[441];
            case "PyTypeObject_tp_descr_set":
                return builtins[442];
            case "PyTypeObject_tp_dict":
                return builtins[443];
            case "PyTypeObject_tp_dictoffset":
                return builtins[444];
            case "PyTypeObject_tp_doc":
                return builtins[445];
            case "PyTypeObject_tp_finalize":
                return builtins[446];
            case "PyTypeObject_tp_flags":
                return builtins[447];
            case "PyTypeObject_tp_free":
                return builtins[448];
            case "PyTypeObject_tp_getattr":
                return builtins[449];
            case "PyTypeObject_tp_getattro":
                return builtins[450];
            case "PyTypeObject_tp_getset":
                return builtins[451];
            case "PyTypeObject_tp_hash":
                return builtins[452];
            case "PyTypeObject_tp_init":
                return builtins[453];
            case "PyTypeObject_tp_is_gc":
                return builtins[454];
            case "PyTypeObject_tp_itemsize":
                return builtins[455];
            case "PyTypeObject_tp_iter":
                return builtins[456];
            case "PyTypeObject_tp_iternext":
                return builtins[457];
            case "PyTypeObject_tp_members":
                return builtins[458];
            case "PyTypeObject_tp_methods":
                return builtins[459];
            case "PyTypeObject_tp_mro":
                return builtins[460];
            case "PyTypeObject_tp_name":
                return builtins[461];
            case "PyTypeObject_tp_new":
                return builtins[462];
            case "PyTypeObject_tp_repr":
                return builtins[463];
            case "PyTypeObject_tp_richcompare":
                return builtins[464];
            case "PyTypeObject_tp_setattr":
                return builtins[465];
            case "PyTypeObject_tp_setattro":
                return builtins[466];
            case "PyTypeObject_tp_str":
                return builtins[467];
            case "PyTypeObject_tp_subclasses":
                return builtins[468];
            case "PyTypeObject_tp_traverse":
                return builtins[469];
            case "PyTypeObject_tp_vectorcall":
                return builtins[470];
            case "PyTypeObject_tp_vectorcall_offset":
                return builtins[471];
            case "PyTypeObject_tp_version_tag":
                return builtins[472];
            case "PyTypeObject_tp_weaklist":
                return builtins[473];
            case "PyTypeObject_tp_weaklistoffset":
                return builtins[474];
            case "PyUnicodeObject_data":
                return builtins[475];
            case "PyVarObject_ob_size":
                return builtins[476];
            case "dummy":
                return builtins[477];
            case "mmap_object_data":
                return builtins[478];
        }
        return null;
    }
    // @formatter:on
    // {{end CAPI_BUILTINS}}
}
