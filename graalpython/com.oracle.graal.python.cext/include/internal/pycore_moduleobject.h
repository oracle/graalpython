/* Copyright (c) 2022, 2026, Oracle and/or its affiliates.
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

extern void _PyModule_Clear(PyObject *);
extern void _PyModule_ClearDict(PyObject *);
extern int _PyModuleSpec_IsInitializing(PyObject *);
extern int _PyModuleSpec_GetFileOrigin(PyObject *, PyObject **);
extern int _PyModule_IsPossiblyShadowing(PyObject *);

extern int _PyModule_IsExtension(PyObject *obj);

typedef struct {
    PyObject_HEAD
    PyObject *md_dict;
    PyModuleDef *md_def;
    void *md_state;
    PyObject *md_weaklist;
    // for logging purposes after md_dict is cleared
    PyObject *md_name;
#ifdef Py_GIL_DISABLED
    void *md_gil;
#endif
} PyModuleObject;

static inline PyModuleDef* _PyModule_GetDef(PyObject *mod) {
    return PyModule_GetDef(mod);
}

static inline void* _PyModule_GetState(PyObject* mod) {
    return PyModule_GetState(mod);
}

static inline PyObject* _PyModule_GetDict(PyObject *mod) {
    return PyModule_GetDict(mod);
}

PyObject* _Py_module_getattro_impl(PyModuleObject *m, PyObject *name, int suppress);
PyObject* _Py_module_getattro(PyModuleObject *m, PyObject *name);

#ifdef __cplusplus
}
#endif
#endif /* !Py_INTERNAL_MODULEOBJECT_H */
