/* Copyright (c) 2022, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_METHODOBJECT_H
#  error "this header file must not be included directly"
#endif

// PyCFunctionObject structure

typedef struct {
    PyObject_HEAD
    PyMethodDef *m_ml; /* Description of the C function to call */
    PyObject    *m_self; /* Passed as 'self' arg to the C func, can be NULL */
    PyObject    *m_module; /* The __module__ attribute, can be anything */
    PyObject    *m_weakreflist; /* List of weak references */
    vectorcallfunc vectorcall;
} PyCFunctionObject;

#define _PyCFunctionObject_CAST(func) \
    (assert(PyCFunction_Check(func)), \
     _Py_CAST(PyCFunctionObject*, (func)))


// PyCMethodObject structure

typedef struct {
    PyCFunctionObject func;
    PyTypeObject *mm_class; /* Class that defines this method */
} PyCMethodObject;

#define _PyCMethodObject_CAST(func) \
    (assert(PyCMethod_Check(func)), \
     _Py_CAST(PyCMethodObject*, (func)))

PyAPI_DATA(PyTypeObject) PyCMethod_Type;

#define PyCMethod_CheckExact(op) Py_IS_TYPE((op), &PyCMethod_Type)
#define PyCMethod_Check(op) PyObject_TypeCheck((op), &PyCMethod_Type)

// GraalPy public API functions to replace struct access. Return borrowed references
PyAPI_FUNC(PyTypeObject *) GraalPyCMethod_GetClass(PyObject *func);
PyAPI_FUNC(const char*) GraalPyCFunction_GetDoc(PyObject *func);
PyAPI_FUNC(void) GraalPyCFunction_SetDoc(PyObject *func, const char *doc);
PyAPI_FUNC(PyObject*) GraalPyCFunction_GetModule(PyObject* a);
PyAPI_FUNC(PyMethodDef*) GraalPyCFunction_GetMethodDef(PyObject* a);
PyAPI_FUNC(void) GraalPyCFunction_SetModule(PyObject* a, PyObject* b);
PyAPI_FUNC(void) GraalPyCFunction_SetMethodDef(PyObject* a, PyMethodDef *b);

/* Static inline functions for direct access to these values.
   Type checks are *not* done, so use with care. */
static inline PyCFunction PyCFunction_GET_FUNCTION(PyObject *func) {
   return PyCFunction_GetFunction(func);
}
#define PyCFunction_GET_FUNCTION(func) PyCFunction_GET_FUNCTION(_PyObject_CAST(func))

static inline PyObject* PyCFunction_GET_SELF(PyObject *func_obj) {
    return PyCFunction_GetSelf(func_obj);
}
#define PyCFunction_GET_SELF(func) PyCFunction_GET_SELF(_PyObject_CAST(func))

static inline int PyCFunction_GET_FLAGS(PyObject *func) {
    return PyCFunction_GetFlags(func);
}
#define PyCFunction_GET_FLAGS(func) PyCFunction_GET_FLAGS(_PyObject_CAST(func))

static inline PyTypeObject* PyCFunction_GET_CLASS(PyObject *func_obj) {
    return GraalPyCMethod_GetClass(func_obj);
}
#define PyCFunction_GET_CLASS(func) PyCFunction_GET_CLASS(_PyObject_CAST(func))
