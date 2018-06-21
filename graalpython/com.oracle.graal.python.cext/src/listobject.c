/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

PyTypeObject PyList_Type = PY_TRUFFLE_TYPE("list", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_LIST_SUBCLASS, sizeof(PyListObject));

PyObject* PyList_New(Py_ssize_t size) {
    return UPCALL_CEXT_O("PyList_New", size);
}

PyObject* PyList_GetItem(PyObject *op, Py_ssize_t i) {
    return UPCALL_CEXT_O("PyList_GetItem", native_to_java(op), i);
}

int PyList_SetItem(PyObject *op, Py_ssize_t i, PyObject *newitem) {
    return UPCALL_CEXT_I("PyList_SetItem", native_to_java(op), i, native_to_java(newitem));
}

int PyList_Append(PyObject *op, PyObject *newitem) {
	if (newitem == NULL) {
		PyErr_BadInternalCall();
		return -1;
	}
    return UPCALL_CEXT_I("PyList_Append", native_to_java(op), native_to_java(newitem));
}

PyObject* PyList_AsTuple(PyObject *v) {
    if (v == NULL) {
        PyErr_BadInternalCall();
        return NULL;
    }
    return UPCALL_CEXT_O("PyList_AsTuple", native_to_java(v));
}

PyObject* PyList_GetSlice(PyObject *a, Py_ssize_t ilow, Py_ssize_t ihigh) {
    return UPCALL_CEXT_O("PyList_GetSlice", native_to_java(a), ilow, ihigh);
}

Py_ssize_t PyList_Size(PyObject *op) {
    return UPCALL_CEXT_I("PyList_Size", native_to_java(op));
}
