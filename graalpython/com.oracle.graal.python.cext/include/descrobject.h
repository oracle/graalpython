/* Copyright (c) 2018, 2022, Oracle and/or its affiliates.
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

typedef struct PyGetSetDef {
    const char *name;
    getter get;
    setter set;
    const char *doc;
    void *closure;
} PyGetSetDef;

#ifndef Py_LIMITED_API
typedef PyObject *(*wrapperfunc)(PyObject *self, PyObject *args,
                                 void *wrapped);

typedef PyObject *(*wrapperfunc_kwds)(PyObject *self, PyObject *args,
                                      void *wrapped, PyObject *kwds);

struct wrapperbase {
    const char *name;
    int offset;
    void *function;
    wrapperfunc wrapper;
    const char *doc;
    int flags;
    PyObject *name_strobj;
};

/* Flags for above struct */
#define PyWrapperFlag_KEYWORDS 1 /* wrapper function takes keyword args */

/* Various kinds of descriptor objects */

typedef struct {
    PyObject_HEAD
    PyTypeObject *Py_HIDE_IMPL_FIELD(d_type);
    PyObject *Py_HIDE_IMPL_FIELD(d_name);
    PyObject *Py_HIDE_IMPL_FIELD(d_qualname);
} PyDescrObject;

#define PyDescr_COMMON PyDescrObject d_common

#define PyDescr_TYPE(x) (PyDescrObject_GetType((PyObject*) (x)))
#define PyDescr_NAME(x) (PyDescrObject_GetName((PyObject*) (x)))

typedef struct {
    PyDescr_COMMON;
    PyMethodDef *Py_HIDE_IMPL_FIELD(d_method);
    vectorcallfunc vectorcall;
} PyMethodDescrObject;

typedef struct {
    PyDescr_COMMON;
    struct PyMemberDef *d_member;
} PyMemberDescrObject;

typedef struct {
    PyDescr_COMMON;
    PyGetSetDef *d_getset;
} PyGetSetDescrObject;

typedef struct {
    PyDescr_COMMON;
    struct wrapperbase *d_base;
    void *d_wrapped; /* This can be any function pointer */
} PyWrapperDescrObject;
#endif /* Py_LIMITED_API */

PyAPI_DATA(PyTypeObject) PyClassMethodDescr_Type;
PyAPI_DATA(PyTypeObject) PyGetSetDescr_Type;
PyAPI_DATA(PyTypeObject) PyMemberDescr_Type;
PyAPI_DATA(PyTypeObject) PyMethodDescr_Type;
PyAPI_DATA(PyTypeObject) PyWrapperDescr_Type;
PyAPI_DATA(PyTypeObject) PyDictProxy_Type;
#ifndef Py_LIMITED_API
PyAPI_DATA(PyTypeObject) _PyMethodWrapper_Type;
#endif /* Py_LIMITED_API */

PyAPI_FUNC(PyObject *) PyDescr_NewMethod(PyTypeObject *, PyMethodDef *);
PyAPI_FUNC(PyObject *) PyDescr_NewClassMethod(PyTypeObject *, PyMethodDef *);
struct PyMemberDef; /* forward declaration for following prototype */
PyAPI_FUNC(PyObject *) PyDescr_NewMember(PyTypeObject *,
                                               struct PyMemberDef *);
PyAPI_FUNC(PyObject *) PyDescr_NewGetSet(PyTypeObject *,
                                               struct PyGetSetDef *);
#ifndef Py_LIMITED_API
PyAPI_FUNC(PyObject *) PyDescr_NewWrapper(PyTypeObject *,
                                                struct wrapperbase *, void *);
PyAPI_FUNC(int) PyDescr_IsData(PyObject *);
#endif

PyAPI_FUNC(PyObject *) PyDictProxy_New(PyObject *);
PyAPI_FUNC(PyObject *) PyWrapper_New(PyObject *, PyObject *);

PyAPI_FUNC(PyMethodDef*) PyMethodDescrObject_GetMethod(PyObject*);
PyAPI_FUNC(PyTypeObject*) PyDescrObject_GetType(PyObject*);
PyAPI_FUNC(PyObject*) PyDescrObject_GetName(PyObject*);


PyAPI_DATA(PyTypeObject) PyProperty_Type;
#ifdef __cplusplus
}
#endif
#endif /* !Py_DESCROBJECT_H */

