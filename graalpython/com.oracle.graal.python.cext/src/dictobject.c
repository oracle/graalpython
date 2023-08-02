/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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


PyObject* _PyDict_NewPresized(Py_ssize_t minused) {
    /* we ignore requests to capacity for now */
    return GraalPyDict_New();
}

/* Same as PyDict_GetItemWithError() but with hash supplied by caller.
   This returns NULL *with* an exception set if an exception occurred.
   It returns NULL *without* an exception set if the key wasn't present.
*/
PyObject* _PyDict_GetItem_KnownHash(PyObject *d, PyObject *k, Py_hash_t hash) {
    /* we ignore the known hash for now */
    return PyDict_GetItemWithError(d, k);
}

PyObject* _PyDict_GetItemIdWithError(PyObject *dp, struct _Py_Identifier *key)
{
    PyObject *kv;
    kv = _PyUnicode_FromId(key); /* borrowed */
    if (kv == NULL)
        return NULL;
    return PyDict_GetItemWithError(dp, kv);
}

int PyDict_Next(PyObject *d, Py_ssize_t *ppos, PyObject **pkey, PyObject **pvalue) {
	return _PyDict_Next(d, ppos, pkey, pvalue, NULL);
}

int _PyDict_Next(PyObject *d, Py_ssize_t *ppos, PyObject **pkey, PyObject **pvalue, Py_hash_t *phash) {
    PyObject *tresult = GraalPyTruffleDict_Next(d, *ppos);
    if (tresult == NULL) {
    	if(pkey != NULL) {
    		*pkey = NULL;
    	}
    	if(pvalue != NULL) {
    		*pvalue = NULL;
    	}
    	return 0;
    }
    if (pkey != NULL) {
    	*pkey = PyTuple_GetItem(tresult, 0);
    }
    if (pvalue != NULL) {
    	*pvalue = PyTuple_GetItem(tresult, 1);
    }
    if (phash != NULL) {
    	*phash = PyLong_AsSsize_t(PyTuple_GetItem(tresult, 2));
    }
    *ppos = PyLong_AsSsize_t(PyTuple_GetItem(tresult, 3));
    Py_DECREF(tresult);
    return 1;

}

PyObject* PyDict_GetItemString(PyObject *d, const char *key) {
	CALL_WITH_STRING(key, PyObject*, NULL, GraalPyDict_GetItem, d, string);
}

PyObject* _PyDict_GetItemStringWithError(PyObject *d, const char *key){
	CALL_WITH_STRING(key, PyObject*, NULL, GraalPyDict_GetItemWithError, d, string);
}

int PyDict_SetItemString(PyObject *d, const char *key, PyObject *item) {
	CALL_WITH_STRING(key, int, -1, GraalPyDict_SetItem, d, string, item);
}

int PyDict_DelItemString(PyObject *d, const char *key) {
	CALL_WITH_STRING(key, int, -1, GraalPyDict_DelItem, d, string);
}

PyAPI_FUNC(PyObject*) _PyObject_GenericGetDict(PyObject* obj) {
    PyObject** dictptr = _PyObject_GetDictPtr(obj);
    if (dictptr == NULL) {
        return NULL;
    }
    PyObject* dict = *dictptr;
    if (dict == NULL) {
        *dictptr = dict = PyDict_New();
    }
    Py_XINCREF(dict);
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

/* Taken from CPython */
int
_PyDict_ContainsId(PyObject *op, struct _Py_Identifier *key)
{
    PyObject *kv = _PyUnicode_FromId(key); /* borrowed */
    if (kv == NULL) {
        return -1;
    }
    return PyDict_Contains(op, kv);
}

/* Taken from CPython */
int
_PyDict_SetItemId(PyObject *v, struct _Py_Identifier *key, PyObject *item)
{
    PyObject *kv;
    kv = _PyUnicode_FromId(key); /* borrowed */
    if (kv == NULL)
        return -1;
    return PyDict_SetItem(v, kv, item);
}
