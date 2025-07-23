/* Copyright (c) 2023, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2023 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_FLOATOBJECT_H
#  error "this header file must not be included directly"
#endif

typedef struct {
    PyObject_HEAD
    double ob_fval;
} PyFloatObject;

#define _PyFloat_CAST(op) \
    (assert(PyFloat_Check(op)), _Py_CAST(PyFloatObject*, op))

// GraalPy public API
PyAPI_FUNC(double) GraalPyFloat_AS_DOUBLE(PyObject* op);

// Static inline version of PyFloat_AsDouble() trading safety for speed.
// It doesn't check if op is a double object.
static inline double PyFloat_AS_DOUBLE(PyObject *op) {
    // GraalPy change
    return GraalPyFloat_AS_DOUBLE(op);
}
#define PyFloat_AS_DOUBLE(op) PyFloat_AS_DOUBLE(_PyObject_CAST(op))


PyAPI_FUNC(int) PyFloat_Pack2(double x, char *p, int le);
PyAPI_FUNC(int) PyFloat_Pack4(double x, char *p, int le);
PyAPI_FUNC(int) PyFloat_Pack8(double x, char *p, int le);

PyAPI_FUNC(double) PyFloat_Unpack2(const char *p, int le);
PyAPI_FUNC(double) PyFloat_Unpack4(const char *p, int le);
PyAPI_FUNC(double) PyFloat_Unpack8(const char *p, int le);
