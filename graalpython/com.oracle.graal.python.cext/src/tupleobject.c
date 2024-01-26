/* Copyright (c) 2018, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "capi.h"


/* Tuples */

NO_INLINE
PyObject *
PyTuple_Pack(Py_ssize_t n, ...)
{
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

PyObject *
tuple_subtype_new(PyTypeObject *type, PyObject *iterable)
{
    PyObject *tmp, *newobj, *item;
    Py_ssize_t i, n;

    assert(PyType_IsSubtype(type, &PyTuple_Type));
    tmp = iterable == NULL ? PyTuple_New(0) : PySequence_Tuple(iterable);
    if (tmp == NULL)
        return NULL;
    assert(PyTuple_Check(tmp));
    n = PyTuple_GET_SIZE(tmp);

    /* GraalPy note: we cannot call type->tp_alloc here because managed subtypes don't inherit tp_alloc but get a generic one.
     * In CPython tuple uses the generic one to begin with, so they don't have this problem
     */
    newobj = PyTruffle_Tuple_Alloc(type, n);
    if (newobj == NULL) {
        return NULL;
    }
    for (i = 0; i < n; i++) {
        item = PyTuple_GetItem(tmp, i);
        Py_INCREF(item);
        ((PyTupleObject*) newobj)->ob_item[i] = item; // PyTuple_SETITEM
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

PyObject*
PyTuple_GetItem(PyObject* a, Py_ssize_t b) {
#ifdef GRAALVM_PYTHON_LLVM_MANAGED
    return GraalPyTruffleTuple_GetItem(a, b);
#else /* GRAALVM_PYTHON_LLVM_MANAGED */
    if (!PyTuple_Check(a)) {
        PyErr_BadInternalCall();
        return NULL;
    }
    PyObject *res;
    PyObject **ob_item;
    if (points_to_py_handle_space(a)) {
        const PyObject *ptr = pointer_to_stub((PyObject *) a);
        ob_item = ((GraalPyVarObject *) ptr)->ob_item;
        if (ob_item == NULL) {
            // native data ptr not set; do upcall
            return GraalPyTruffleTuple_GetItem(a, b);
        }
    } else {
        ob_item = ((PyTupleObject *) a)->ob_item;
    }
    // do index check since directly accessing the items array
    if (b < 0 || b >= Py_SIZE(a)) {
        PyErr_SetString(PyExc_IndexError, "tuple index out of range");
        return NULL;
    }
    assert(ob_item != NULL);
    return ob_item[b];
#endif /* GRAALVM_PYTHON_LLVM_MANAGED */
}
