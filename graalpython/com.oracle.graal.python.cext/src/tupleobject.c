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

/* prototype */
PyObject* PyTruffle_Tuple_Alloc(PyTypeObject* cls, Py_ssize_t nitems);

/* tuple type */
PyTypeObject PyTuple_Type = PY_TRUFFLE_TYPE_WITH_ALLOC("tuple", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_TUPLE_SUBCLASS, sizeof(PyTupleObject) - sizeof(PyObject *), PyTruffle_Tuple_Alloc);

/* Tuples */
UPCALL_ID(PyTuple_New);
PyObject* PyTuple_New(Py_ssize_t size) {
    return UPCALL_CEXT_O(_jls_PyTuple_New, size);
}

UPCALL_ID(PyTuple_SetItem);
int PyTuple_SetItem(PyObject* tuple, Py_ssize_t position, PyObject* item) {
    return UPCALL_CEXT_I(_jls_PyTuple_SetItem, native_to_java(tuple), position, native_to_java(item));
}

UPCALL_ID(PyTuple_GetItem);
PyObject* PyTuple_GetItem(PyObject* tuple, Py_ssize_t position) {
    return UPCALL_CEXT_O(_jls_PyTuple_GetItem, native_to_java(tuple), position);
}

UPCALL_ID(PyTuple_Size);
Py_ssize_t PyTuple_Size(PyObject *op) {
    return UPCALL_CEXT_L(_jls_PyTuple_Size, native_to_java(op));
}

UPCALL_ID(PyTuple_GetSlice);
PyObject* PyTuple_GetSlice(PyObject *tuple, Py_ssize_t i, Py_ssize_t j) {
    return UPCALL_CEXT_O(_jls_PyTuple_GetSlice, native_to_java(tuple), i, j);
}

NO_INLINE
PyObject* PyTuple_Pack(Py_ssize_t n, ...) {
    PyObject *result = PyTuple_New(n);
    if (result == NULL) {
        return NULL;
    }
    for (int i = 0; i < n; i++) {
        PyObject *o = polyglot_get_arg(i+1);
        PyTuple_SetItem(result, i, o);
    }
    return result;
}

MUST_INLINE
static PyObject * tuple_create(PyObject *iterable) {
    if (iterable == NULL) {
        return PyTuple_New(0);
    }
    return PySequence_Tuple(iterable);
}

POLYGLOT_DECLARE_TYPE(PyTupleObject);
PyObject * tuple_subtype_new(PyTypeObject *type, PyObject *iterable) {
	PyTupleObject* newobj;
    PyObject *tmp, *item;
    Py_ssize_t i, n;

    assert(PyType_IsSubtype(type, &PyTuple_Type));
    tmp = tuple_create(iterable);
    if (tmp == NULL) {
        return NULL;
    }
    assert(PyTuple_Check(tmp));
    n = PyTuple_GET_SIZE(tmp);

    newobj = (PyTupleObject*) type->tp_alloc(type, n);
    if (newobj == NULL) {
        return NULL;
    }
    newobj->ob_item = (PyObject **) ((char *)newobj + offsetof(PyTupleObject, ob_item) + sizeof(PyObject **));
    newobj = polyglot_from_PyTupleObject(newobj);
    for (i = 0; i < n; i++) {
        item = PyTuple_GetItem(tmp, i);
        Py_INCREF(item);
        PyTuple_SetItem((PyObject*)newobj, i, item);
    }
    Py_DECREF(tmp);

    // This polyglot type cast is important such that we can directly read and
    // write members of the pointer from Java code.
    // Note: the return type is 'PyObject*' to be compatible with CPython
    return (PyObject*) newobj;
}

int PyTruffle_Tuple_SetItem(PyObject* tuple, Py_ssize_t position, PyObject* item) {
    PyTuple_SET_ITEM(tuple, position, item);
    return 0;
}

PyObject* PyTruffle_Tuple_GetItem(PyObject* tuple, Py_ssize_t position) {
    return native_to_java(PyTuple_GET_ITEM(tuple, position));
}

PyObject* PyTruffle_Tuple_Alloc(PyTypeObject* cls, Py_ssize_t nitems) {
	/*
	 * TODO(fa): For 'PyVarObjects' (i.e. 'nitems > 0') we increase the size by 'sizeof(void *)'
	 * because this additional pointer can then be used as pointer to the element array.
	 * CPython usually embeds the array in the struct but Sulong doesn't currently support that.
	 * So we allocate space for the additional array pointer.
	 * Also consider any 'PyVarObject' (in particular 'PyTupleObject') if this is fixed.
	 */
	Py_ssize_t size = cls->tp_basicsize + cls->tp_itemsize * nitems + sizeof(PyObject **);
    PyObject* newObj = (PyObject*)PyObject_Malloc(size);
    if(cls->tp_dictoffset) {
    	*((PyObject **) ((char *)newObj + cls->tp_dictoffset)) = NULL;
    }
    Py_TYPE(newObj) = cls;
    if (nitems > 0) {
        ((PyVarObject*)newObj)->ob_size = nitems;
    }
    return newObj;
}

