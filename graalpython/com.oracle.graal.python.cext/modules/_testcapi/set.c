/* Copyright (c) 2024, 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2024 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "parts.h"
#include "util.h"

static PyObject *
set_get_size(PyObject *self, PyObject *obj)
{
    NULLABLE(obj);
    RETURN_SIZE(PySet_GET_SIZE(obj));
}

static PyMethodDef test_methods[] = {
    {"set_get_size", set_get_size, METH_O},

    {NULL},
};

int
_PyTestCapi_Init_Set(PyObject *m)
{
    if (PyModule_AddFunctions(m, test_methods) < 0) {
        return -1;
    }

    return 0;
}
