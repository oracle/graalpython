/* Copyright (c) 2022, Oracle and/or its affiliates.
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

#include "tupleobject.h"   /* _PyTuple_CAST() */

#define _PyTuple_ITEMS(op) (_PyTuple_CAST(op)->ob_item)

PyAPI_FUNC(PyObject *) _PyTuple_FromArray(PyObject *const *, Py_ssize_t);

#ifdef __cplusplus
}
#endif
#endif   /* !Py_INTERNAL_TUPLE_H */
