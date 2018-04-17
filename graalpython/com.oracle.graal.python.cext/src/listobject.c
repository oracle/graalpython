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

PyObject* PyList_New(Py_ssize_t size) {
    PyObject* result = truffle_invoke(PY_TRUFFLE_CEXT, "PyList_New", size, ERROR_MARKER);
    if (result == ERROR_MARKER) {
        return NULL;
    } else {
        return to_sulong(result);
    }
}

PyObject* PyList_GetItem(PyObject *op, Py_ssize_t i) {
    PyObject* result = truffle_invoke(PY_TRUFFLE_CEXT, "PyList_GetItem", to_java(op), i, ERROR_MARKER);
    if (result == ERROR_MARKER) {
        return NULL;
    } else {
        return to_sulong(result);
    }
}

int PyList_SetItem(PyObject *op, Py_ssize_t i, PyObject *newitem) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyList_SetItem", to_java(op), i, to_java(newitem));
}

int PyList_Append(PyObject *op, PyObject *newitem) {
	if (newitem == NULL) {
		PyErr_BadInternalCall();
		return -1;
	}
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyList_Append", to_java(op), to_java(newitem));
}

PyObject* PyList_AsTuple(PyObject *v) {
    if (v == NULL) {
        PyErr_BadInternalCall();
        return NULL;
    }
    PyObject* result = truffle_invoke(PY_TRUFFLE_CEXT, "PyList_AsTuple", to_java(v), ERROR_MARKER);
    if (result == ERROR_MARKER) {
        return NULL;
    } else {
        return to_sulong(result);
    }
}

PyObject* PyList_GetSlice(PyObject *a, Py_ssize_t ilow, Py_ssize_t ihigh) {
    PyObject* result = truffle_invoke(PY_TRUFFLE_CEXT, "PyList_GetSlice", to_java(a), ilow, ihigh, ERROR_MARKER);
    if (result == ERROR_MARKER) {
        return NULL;
    } else {
        return to_sulong(result);
    }
}

Py_ssize_t PyList_Size(PyObject *op) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyList_Size", to_java(op));
}
