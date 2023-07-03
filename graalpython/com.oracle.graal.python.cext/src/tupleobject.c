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


/* Tuples */

NO_INLINE
PyObject* PyTuple_Pack(Py_ssize_t n, ...) {
    va_list vargs;
    va_start(vargs, n);
    PyObject *result = PyTuple_New(n);
    if (result == NULL) {
        goto end;
    }
    for (int i = 0; i < n; i++) {
        PyObject *o = va_arg(vargs, PyObject *);
        Py_XINCREF(o);
        PyTuple_SetItem(result, i, o);
    }
 end:
    va_end(vargs);
    return result;
}

PyObject* PyTruffle_Tuple_Alloc(PyTypeObject* cls, Py_ssize_t nitems);

POLYGLOT_DECLARE_TYPE(PyTupleObject);
PyObject * tuple_subtype_new(PyTypeObject *type, PyObject *iterable) {
	PyTupleObject* newobj;
    PyObject *tmp, *item;
    Py_ssize_t i, n;

    assert(PyType_IsSubtype(type, &PyTuple_Type));
    tmp = iterable == NULL ? PyTuple_New(0) : PySequence_Tuple(iterable);
    if (tmp == NULL) {
        return NULL;
    }
    assert(PyTuple_Check(tmp));
    n = PyTuple_GET_SIZE(tmp);

    /* GraalPy note: we cannot call type->tp_alloc here because managed subtypes don't inherit tp_alloc but get a generic one.
     * In CPython tuple uses the generic one to begin with, so they don't have this problem
     */
    newobj = (PyTupleObject*) PyTruffle_Tuple_Alloc(type, n);
    if (newobj == NULL) {
        return NULL;
    }

    // This polyglot type cast is important such that we can directly read and
    // write members of the pointer from Java code.
    // Note: the return type is 'PyObject*' to be compatible with CPython
    newobj = polyglot_from_PyTupleObject(newobj);

    for (i = 0; i < n; i++) {
        item = PyTuple_GetItem(tmp, i);
        Py_INCREF(item);
        newobj->ob_item[i] = item; // PyTuple_SETITEM
    }
    Py_DECREF(tmp);
    return (PyObject*) newobj;
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
    PyObject_INIT_VAR(newObj, cls, nitems);
    ((PyTupleObject*)newObj)->ob_item = (PyObject **) ((char *)newObj + offsetof(PyTupleObject, ob_item) + sizeof(PyObject **));
    return newObj;
}

void PyTruffle_Tuple_Dealloc(PyTupleObject* self) {
    Py_ssize_t len =  PyTuple_GET_SIZE(self);
    if (len > 0) {
        Py_ssize_t i = len;
        while (--i >= 0) {
            Py_XDECREF(self->ob_item[i]);
        }
    }
    Py_TYPE(self)->tp_free((PyObject *)self);
}

void* PyTruffle_NativeTupleItems(PyTupleObject* tuple) {
    return polyglot_from_PyObjectPtr_array(tuple->ob_item, tuple->ob_base.ob_size);
}

