/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "capi.h"

PyTypeObject PyEllipsis_Type = PY_TRUFFLE_TYPE("ellipsis", &PyType_Type, Py_TPFLAGS_DEFAULT, 0);
PyTypeObject PySlice_Type = PY_TRUFFLE_TYPE("slice", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, sizeof(PySliceObject));

PyObject _Py_EllipsisObject = {
    _PyObject_EXTRA_INIT
    1, &PyEllipsis_Type
};

UPCALL_ID(PySlice_GetIndicesEx);
int PySlice_GetIndicesEx(PyObject *_r, Py_ssize_t length,
                         Py_ssize_t *start, Py_ssize_t *stop, Py_ssize_t *step,
                         Py_ssize_t *slicelength) {
    PySliceObject *r = (PySliceObject*)_r;
    PyObject* result = UPCALL_CEXT_O(_jls_PySlice_GetIndicesEx, native_to_java(r->start), native_to_java(r->stop), native_to_java(r->step), length);
    if (result == NULL) {
        return -1;
    }
    *start = PyLong_AsSsize_t(PyTuple_GetItem(result, 0));
    *stop = PyLong_AsSsize_t(PyTuple_GetItem(result, 1));
    *step =  PyLong_AsSsize_t(PyTuple_GetItem(result, 2));
    *slicelength =  PyLong_AsSsize_t(PyTuple_GetItem(result, 3));
    return 0;
}

int PySlice_Unpack(PyObject *_r, Py_ssize_t *start, Py_ssize_t *stop, Py_ssize_t *step) {
    PySliceObject *r = (PySliceObject*)_r;
    PyObject* result = UPCALL_CEXT_O(_jls_PySlice_GetIndicesEx, native_to_java(r->start), native_to_java(r->stop), native_to_java(r->step), PY_SSIZE_T_MAX);
    if (result == NULL) {
        return -1;
    }
    *start = PyLong_AsSsize_t(PyTuple_GetItem(result, 0));
    *stop = PyLong_AsSsize_t(PyTuple_GetItem(result, 1));
    *step = PyLong_AsSsize_t(PyTuple_GetItem(result, 2));
    return 0;
}

Py_ssize_t PySlice_AdjustIndices(Py_ssize_t length, Py_ssize_t *start, Py_ssize_t *stop, Py_ssize_t step) {
    PyObject* result = UPCALL_CEXT_O(_jls_PySlice_GetIndicesEx, *start, *stop, step, length);
    if (result == NULL) {
        return -1;
    }
    *start = PyLong_AsSsize_t(PyTuple_GetItem(result, 0));
    *stop = PyLong_AsSsize_t(PyTuple_GetItem(result, 1));
    return PyLong_AsSsize_t(PyTuple_GetItem(result, 3)); // adjusted length
}

UPCALL_ID(PySlice_New);
PyObject* PySlice_New(PyObject* start, PyObject *stop, PyObject *step) {
    return UPCALL_CEXT_O(_jls_PySlice_New, native_to_java(start), native_to_java(stop), native_to_java(step));
}
