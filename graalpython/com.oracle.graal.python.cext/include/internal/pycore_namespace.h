/* Copyright (c) 2023, 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2023 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
// Simple namespace object interface

#ifndef Py_INTERNAL_NAMESPACE_H
#define Py_INTERNAL_NAMESPACE_H
#ifdef __cplusplus
extern "C" {
#endif

#ifndef Py_BUILD_CORE
#  error "this header requires Py_BUILD_CORE define"
#endif

extern PyTypeObject _PyNamespace_Type;

// Export for '_testmultiphase' shared extension
PyAPI_FUNC(PyObject*) _PyNamespace_New(PyObject *kwds);

#ifdef __cplusplus
}
#endif
#endif  // !Py_INTERNAL_NAMESPACE_H
