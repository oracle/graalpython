/* Copyright (c) 2022, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_FILEUTILS_H
#  error "this header file must not be included directly"
#endif

// Used by _testcapi which must not use the internal C API
PyAPI_FUNC(FILE*) _Py_fopen_obj(
    PyObject *path,
    const char *mode);
