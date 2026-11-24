/* Copyright (c) 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2026 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "parts.h"
#include "util.h"


// Test functions, not macros
#undef Py_CompileString


// Test Py_CompileString()
static PyObject*
run_compilestring(PyObject *mod, PyObject *args)
{
    const char *str;
    const char *filename;
    int start;

    if (!PyArg_ParseTuple(args, "yyi", &str, &filename, &start)) {
        return NULL;
    }

    return Py_CompileString(str, filename, start);
}


static PyMethodDef test_methods[] = {
    {"run_compilestring", run_compilestring, METH_VARARGS},
    {NULL},
};

int
_PyTestLimitedCAPI_Init_Run(PyObject *m)
{
    return PyModule_AddFunctions(m, test_methods);
}

