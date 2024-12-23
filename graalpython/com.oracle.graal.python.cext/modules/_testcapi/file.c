/* Copyright (c) 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2024 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "parts.h"
#include "util.h"


static PyMethodDef test_methods[] = {
    {NULL},
};

int
_PyTestCapi_Init_File(PyObject *m)
{
    if (PyModule_AddFunctions(m, test_methods) < 0){
        return -1;
    }

    return 0;
}
