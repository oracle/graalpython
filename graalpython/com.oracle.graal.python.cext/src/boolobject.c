/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */
#include "capi.h"

#include <stdarg.h>

PyTypeObject PyBool_Type = PY_TRUFFLE_TYPE("bool", &PyType_Type, Py_TPFLAGS_DEFAULT);

// taken from CPython "Python/Objects/boolobject.c"
PyObject *PyBool_FromLong(long ok) {
    PyObject *result;

    if (ok) {
        result = Py_True;
    } else {
        result = Py_False;
    }
    Py_INCREF(result);
    return result;
}
