/* Copyright (c) 2020, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_TUPLEOBJECT_H
#  error "this header file must not be included directly"
#endif

typedef struct {
    PyObject_VAR_HEAD
    /* ob_item contains space for 'ob_size' elements.
       Items must normally not be NULL, except during construction when
       the tuple is not yet visible outside the function that builds it. */
    // Truffle change: PyObject *ob_item[1] doesn't work for us in Sulong
    PyObject **Py_HIDE_IMPL_FIELD(ob_item);
} PyTupleObject;

PyAPI_FUNC(int) _PyTuple_Resize(PyObject **, Py_ssize_t);
PyAPI_FUNC(void) _PyTuple_MaybeUntrack(PyObject *);

/* Cast argument to PyTupleObject* type. */
#define _PyTuple_CAST(op) \
    (assert(PyTuple_Check(op)), _Py_CAST(PyTupleObject*, (op)))

// Macros and static inline functions, trading safety for speed

static inline Py_ssize_t PyTuple_GET_SIZE(PyObject *op) {
    PyTupleObject *tuple = _PyTuple_CAST(op);
    return Py_SIZE(tuple);
}
#if !defined(Py_LIMITED_API) || Py_LIMITED_API+0 < 0x030b0000
#  define PyTuple_GET_SIZE(op) PyTuple_GET_SIZE(_PyObject_CAST(op))
#endif

PyAPI_FUNC(PyObject *) _PyTuple_GET_ITEM(PyObject *, Py_ssize_t);
#define PyTuple_GET_ITEM(op, i) _PyTuple_GET_ITEM(_PyObject_CAST(op), (i))

// GraalPy-specific
PyAPI_FUNC(PyObject **) PyTruffleTuple_GetItems(PyObject *op);

// GraalPy change: Export PyTuple_SET_ITEM as regular API function to use in PyO3 and others
PyAPI_FUNC(void) PyTuple_SET_ITEM(PyObject*, Py_ssize_t, PyObject*);

/* Inline function to be used in the PyTuple_SET_ITEM macro. */
static inline void graalpy_tuple_set_item(PyObject *op, Py_ssize_t index, PyObject *value) {
    PyTruffleTuple_GetItems(op)[index] = value;
}
#define PyTuple_SET_ITEM(op, index, value) \
    graalpy_tuple_set_item(_PyObject_CAST(op), (index), _PyObject_CAST(value))

PyAPI_FUNC(void) _PyTuple_DebugMallocStats(FILE *out);
