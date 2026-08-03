/* Copyright (c) 2024, 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2024 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "parts.h"
#include "util.h"
#include "clinic/file.c.h"


/*[clinic input]
module _testcapi
[clinic start generated code]*/
/*[clinic end generated code: output=da39a3ee5e6b4b0d input=6361033e795369fc]*/


/*[clinic input]
_testcapi.pyfile_newstdprinter

    fd: int
    /

[clinic start generated code]*/

static PyObject *
_testcapi_pyfile_newstdprinter_impl(PyObject *module, int fd)
/*[clinic end generated code: output=8a2d1c57b6892db3 input=442f1824142262ea]*/
{
    return PyFile_NewStdPrinter(fd);
}


static PyMethodDef test_methods[] = {
    _TESTCAPI_PYFILE_NEWSTDPRINTER_METHODDEF
    {NULL},
};

int
_PyTestCapi_Init_File(PyObject *m)
{
    return PyModule_AddFunctions(m, test_methods);
}
