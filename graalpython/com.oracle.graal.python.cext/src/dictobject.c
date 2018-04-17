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

/* Dicts */
PyObject* PyDict_New(void) {
    return truffle_invoke(PY_TRUFFLE_CEXT, "PyDict_New");
}

int PyDict_SetItem(PyObject* d, PyObject* k, PyObject* v) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyDict_SetItem", to_java(d), to_java(k), to_java(v));
}

PyObject* PyDict_GetItem(PyObject* d, PyObject* k) {
    void* result = to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyDict_GetItem", to_java(d), to_java(k)));
    if (result == Py_None) {
        return NULL;
    } else {
        return to_sulong(result);
    }
}

int PyDict_DelItem(PyObject *d, PyObject *key) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyDict_DelItem", to_java(d), to_java(key));
}


int PyDict_Next(PyObject *d, Py_ssize_t *ppos, PyObject **pkey, PyObject **pvalue) {
    PyObject *tresult = truffle_invoke(PY_TRUFFLE_CEXT, "PyDict_Next", to_java(d), *ppos, ERROR_MARKER);
    if (tresult == ERROR_MARKER) {
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
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyDict_Size", to_java(d));
}

PyObject * PyDict_Copy(PyObject *d) {
    return to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyDict_Copy", to_java(d)));
}

/* Return 1 if `key` is in dict `op`, 0 if not, and -1 on error. */
int PyDict_Contains(PyObject *d, PyObject *key) {
    return truffle_invoke_i(to_java(d), "__contains__", to_java(key));
}

PyObject * PyDict_GetItemString(PyObject *v, const char *key) {
    return PyDict_GetItem(v, PyUnicode_FromString(key));
}

int PyDict_SetItemString(PyObject *v, const char *key, PyObject *item) {
    truffle_invoke(to_java(v), "__setitem__", PyUnicode_FromString(key), to_java(item));
    return 0;
}

int PyDict_DelItemString(PyObject *d, const char *key) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyDict_DelItem", to_java(d), PyUnicode_FromString(key));
}
