/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

PyTypeObject PyDict_Type = PY_TRUFFLE_TYPE("dict", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_DICT_SUBCLASS, sizeof(PyDictObject));

UPCALL_ID(PyDict_New);
PyObject* PyDict_New(void) {
    return UPCALL_CEXT_O(_jls_PyDict_New);
}

UPCALL_ID(PyDict_SetItem);
int PyDict_SetItem(PyObject* d, PyObject* k, PyObject* v) {
    return UPCALL_CEXT_I(_jls_PyDict_SetItem, native_to_java(d), native_to_java(k), native_to_java(v));
}

UPCALL_ID(PyDict_SetItem_KnownHash);
int _PyDict_SetItem_KnownHash(PyObject *d, PyObject *k, PyObject *v, Py_hash_t hash) {
    return UPCALL_CEXT_I(_jls_PyDict_SetItem_KnownHash, native_to_java(d), native_to_java(k), native_to_java(v), hash);
}

UPCALL_ID(PyDict_GetItem);
PyObject* PyDict_GetItem(PyObject* d, PyObject* k) {
    return UPCALL_CEXT_O(_jls_PyDict_GetItem, native_to_java(d), native_to_java(k));
}

UPCALL_ID(PyDict_GetItemWithError);
PyObject* PyDict_GetItemWithError(PyObject* d, PyObject* k) {
    return UPCALL_CEXT_O(_jls_PyDict_GetItemWithError, native_to_java(d), native_to_java(k));
}

PyObject* _PyDict_GetItemId(PyObject* d, _Py_Identifier* id) {
    return PyDict_GetItemString(d, id->string);
}

UPCALL_ID(PyDict_DelItem);
int PyDict_DelItem(PyObject *d, PyObject *k) {
    return UPCALL_CEXT_I(_jls_PyDict_DelItem, native_to_java(d), native_to_java(k));
}


int PyDict_Next(PyObject *d, Py_ssize_t *ppos, PyObject **pkey, PyObject **pvalue) {
	return _PyDict_Next(d, ppos, pkey, pvalue, NULL);
}

UPCALL_ID(PyDict_Next);
int _PyDict_Next(PyObject *d, Py_ssize_t *ppos, PyObject **pkey, PyObject **pvalue, Py_hash_t *phash) {
    PyObject *tresult = UPCALL_CEXT_O(_jls_PyDict_Next, native_to_java(d), *ppos);
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
    if (phash != NULL) {
    	*phash = PyTuple_GetItem(tresult, 2);
    }
    return 1;

}

UPCALL_ID(PyDict_Size);
Py_ssize_t PyDict_Size(PyObject *d) {
    return UPCALL_CEXT_L(_jls_PyDict_Size, native_to_java(d));
}

UPCALL_ID(PyDict_Copy);
PyObject * PyDict_Copy(PyObject *d) {
    return UPCALL_CEXT_O(_jls_PyDict_Copy, native_to_java(d));
}

/* Return 1 if `key` is in dict `op`, 0 if not, and -1 on error. */
UPCALL_ID(PyDict_Contains);
int PyDict_Contains(PyObject *d, PyObject *k) {
    return UPCALL_CEXT_I(_jls_PyDict_Contains, native_to_java(d), native_to_java(k));
}

PyObject * PyDict_GetItemString(PyObject *d, const char *key) {
    return UPCALL_CEXT_O(_jls_PyDict_GetItem, native_to_java(d), polyglot_from_string(key, SRC_CS));
}

int PyDict_SetItemString(PyObject *d, const char *key, PyObject *item) {
    UPCALL_CEXT_I(_jls_PyDict_SetItem, native_to_java(d), polyglot_from_string(key, SRC_CS), native_to_java(item));
    return 0;
}

int PyDict_DelItemString(PyObject *d, const char *key) {
    return UPCALL_CEXT_I(_jls_PyDict_DelItem, native_to_java(d), polyglot_from_string(key, SRC_CS));
}

int PyDict_Update(PyObject *a, PyObject *b) {
    PyObject* result = UPCALL_O(native_to_java(a), polyglot_from_string("update", SRC_CS), native_to_java(b));
    if (PyErr_Occurred()) {
        return -1;
    } else {
        return 0;
    }
}

PyObject* _PyObject_GenericGetDict(PyObject* obj) {
    PyObject** dictptr = _PyObject_GetDictPtr(obj);
    if (dictptr == NULL) {
        return NULL;
    }
    PyObject* dict = *dictptr;
    if (dict == NULL) {
        *dictptr = dict = PyDict_New();
    }
    return dict;
}

PyObject* PyObject_GenericGetDict(PyObject* obj, void* context) {
    PyObject* d = _PyObject_GenericGetDict(obj);
    if (d == NULL) {
        PyErr_SetString(PyExc_AttributeError, "This object has no __dict__");
    }
    return d;
}

PyObject** _PyObject_GetDictPtr(PyObject* obj) {
    Py_ssize_t dictoffset;

    // relies on the fact that 'tp_dictoffset' is in sync with the corresponding managed class !
    PyTypeObject *tp = Py_TYPE(obj);

    // this would be a different way to do it
    // PyTypeObject *tp = (PyTypeObject*) native_type_to_java(Py_TYPE(obj));

    dictoffset = tp->tp_dictoffset;
    if (dictoffset == 0) {
        return NULL;
    }
    if (dictoffset < 0) {
        Py_ssize_t nitems = ((PyVarObject *)obj)->ob_size;
        if (nitems < 0) {
            nitems = -nitems;
        }

        size_t size = tp->tp_basicsize + nitems * tp->tp_itemsize;
        if (size % SIZEOF_VOID_P != 0) {
            // round to full pointer boundary
            size += SIZEOF_VOID_P - (size % SIZEOF_VOID_P);
        }
        dictoffset += (long)size;
    }
    return (PyObject **) ((char *)obj + dictoffset);
}

void PyDict_Clear(PyObject *obj) {
	(void) UPCALL_O(to_java(obj), polyglot_from_string("clear", SRC_CS));
}

UPCALL_ID(PyDict_Merge);
int PyDict_Merge(PyObject *a, PyObject *b, int override) {
    return UPCALL_CEXT_I(_jls_PyDict_Merge, native_to_java(a), native_to_java(b), override);
}
