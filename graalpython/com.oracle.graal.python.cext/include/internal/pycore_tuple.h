/* Copyright (c) 2022, 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_INTERNAL_TUPLE_H
#define Py_INTERNAL_TUPLE_H
#ifdef __cplusplus
extern "C" {
#endif

#ifndef Py_BUILD_CORE
#  error "this header requires Py_BUILD_CORE define"
#endif

extern void _PyTuple_MaybeUntrack(PyObject *);
extern void _PyTuple_DebugMallocStats(FILE *out);

/* runtime lifecycle */

extern PyStatus _PyTuple_InitGlobalObjects(PyInterpreterState *);


/* other API */

#define _PyTuple_ITEMS(op) (GraalPyTuple_ITEMS(op))

extern PyObject *_PyTuple_FromArray(PyObject *const *, Py_ssize_t);
PyAPI_FUNC(PyObject *)_PyTuple_FromArraySteal(PyObject *const *, Py_ssize_t);

typedef struct {
    PyObject_HEAD
    Py_ssize_t it_index;
    PyTupleObject *it_seq; /* Set to NULL when iterator is exhausted */
} _PyTupleIterObject;

#ifdef __cplusplus
}
#endif
#endif   /* !Py_INTERNAL_TUPLE_H */
