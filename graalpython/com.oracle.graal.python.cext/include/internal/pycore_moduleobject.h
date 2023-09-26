/* Copyright (c) 2022, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_INTERNAL_MODULEOBJECT_H
#define Py_INTERNAL_MODULEOBJECT_H
#ifdef __cplusplus
extern "C" {
#endif

#ifndef Py_BUILD_CORE
#  error "this header requires Py_BUILD_CORE define"
#endif

typedef struct {
    PyObject_HEAD
    PyObject *md_dict;
    PyModuleDef *md_def;
    void *md_state;
    PyObject *md_weaklist;
    // for logging purposes after md_dict is cleared
    PyObject *md_name;
} PyModuleObject;

PyAPI_FUNC(PyModuleDef*) _PyModule_GetDef(PyObject *mod);
PyAPI_FUNC(void*) _PyModule_GetState(PyObject* mod);
PyAPI_FUNC(PyObject*) _PyModule_GetDict(PyObject *mod);

#ifdef __cplusplus
}
#endif
#endif /* !Py_INTERNAL_MODULEOBJECT_H */
