/* Copyright (c) 2018, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
/* Descriptors */
#ifndef Py_DESCROBJECT_H
#define Py_DESCROBJECT_H
#ifdef __cplusplus
extern "C" {
#endif

typedef PyObject *(*getter)(PyObject *, void *);
typedef int (*setter)(PyObject *, PyObject *, void *);

struct PyGetSetDef {
    const char *name;
    getter get;
    setter set;
    const char *doc;
    void *closure;
};

PyAPI_DATA(PyTypeObject) PyClassMethodDescr_Type;
PyAPI_DATA(PyTypeObject) PyGetSetDescr_Type;
PyAPI_DATA(PyTypeObject) PyMemberDescr_Type;
PyAPI_DATA(PyTypeObject) PyMethodDescr_Type;
PyAPI_DATA(PyTypeObject) PyWrapperDescr_Type;
PyAPI_DATA(PyTypeObject) PyDictProxy_Type;
PyAPI_DATA(PyTypeObject) PyProperty_Type;

PyAPI_FUNC(PyObject *) PyDescr_NewMethod(PyTypeObject *, PyMethodDef *);
PyAPI_FUNC(PyObject *) PyDescr_NewClassMethod(PyTypeObject *, PyMethodDef *);
PyAPI_FUNC(PyObject *) PyDescr_NewMember(PyTypeObject *, PyMemberDef *);
PyAPI_FUNC(PyObject *) PyDescr_NewGetSet(PyTypeObject *, PyGetSetDef *);

PyAPI_FUNC(PyObject *) PyDictProxy_New(PyObject *);
PyAPI_FUNC(PyObject *) PyWrapper_New(PyObject *, PyObject *);

// GraalPy-specific
PyAPI_FUNC(PyMethodDef*) PyMethodDescrObject_GetMethod(PyObject*);
PyAPI_FUNC(PyTypeObject*) PyDescrObject_GetType(PyObject*);
PyAPI_FUNC(PyObject*) PyDescrObject_GetName(PyObject*);

#ifndef Py_LIMITED_API
#  define Py_CPYTHON_DESCROBJECT_H
#  include "cpython/descrobject.h"
#  undef Py_CPYTHON_DESCROBJECT_H
#endif

#ifdef __cplusplus
}
#endif
#endif /* !Py_DESCROBJECT_H */
