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

PyTypeObject PyDict_Type = PY_TRUFFLE_TYPE("dict", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_DICT_SUBCLASS, sizeof(PyDictObject));

PyObject* PyDict_New(void) {
    return UPCALL_CEXT_O("PyDict_New");
}

int PyDict_SetItem(PyObject* d, PyObject* k, PyObject* v) {
    return UPCALL_CEXT_I("PyDict_SetItem", native_to_java(d), native_to_java(k), native_to_java(v));
}

PyObject* PyDict_GetItem(PyObject* d, PyObject* k) {
    return UPCALL_CEXT_O("PyDict_GetItem", native_to_java(d), native_to_java(k));
}

int PyDict_DelItem(PyObject *d, PyObject *k) {
    return UPCALL_CEXT_I("PyDict_DelItem", native_to_java(d), native_to_java(k));
}


int PyDict_Next(PyObject *d, Py_ssize_t *ppos, PyObject **pkey, PyObject **pvalue) {
    PyObject *tresult = UPCALL_CEXT_O("PyDict_Next", native_to_java(d), *ppos);
    if (tresult == NULL) {
    	if(pkey != NULL) {
    		*pkey = NULL;
    	}
    	if(pvalue != NULL) {
    		*pvalue = NULL;
    	}
    	return 0;
    }
    (*ppos)++;
    if (pkey != NULL) {
    	*pkey = PyTuple_GetItem(tresult, 0);
    }
    if (pvalue != NULL) {
    	*pvalue = PyTuple_GetItem(tresult, 1);
    }
    return 1;
}

Py_ssize_t PyDict_Size(PyObject *d) {
    return UPCALL_CEXT_L("PyDict_Size", native_to_java(d));
}

PyObject * PyDict_Copy(PyObject *d) {
    return UPCALL_CEXT_O("PyDict_Copy", native_to_java(d));
}

/* Return 1 if `key` is in dict `op`, 0 if not, and -1 on error. */
int PyDict_Contains(PyObject *d, PyObject *k) {
    return UPCALL_CEXT_I("PyDict_Contains", native_to_java(d), native_to_java(k));
}

PyObject * PyDict_GetItemString(PyObject *d, const char *key) {
    return UPCALL_CEXT_O("PyDict_GetItem", native_to_java(d), polyglot_from_string(key, SRC_CS));
}

int PyDict_SetItemString(PyObject *d, const char *key, PyObject *item) {
    UPCALL_CEXT_I("PyDict_SetItem", native_to_java(d), polyglot_from_string(key, SRC_CS), native_to_java(item));
    return 0;
}

int PyDict_DelItemString(PyObject *d, const char *key) {
    return UPCALL_CEXT_I("PyDict_DelItem", native_to_java(d), polyglot_from_string(key, SRC_CS));
}

int PyDict_Update(PyObject *a, PyObject *b) {
    PyObject* result = UPCALL_O(a, "update", b);
    if (PyErr_Occurred()) {
        return -1;
    } else {
        return 0;
    }
}
