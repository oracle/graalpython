/* Copyright (c) 2021, 2022, Oracle and/or its affiliates.
 * Copyright (C) 1996-2021 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
/* Wrap void * pointers to be passed between C modules */

#include "Python.h"

#include "capi.h"

/* Internal structure of PyCapsule */
typedef struct {
    PyObject_HEAD
    void *pointer;
    const char *name;
    void *context;
    PyCapsule_Destructor destructor;
} PyCapsule;

PyTypeObject PyCapsule_Type = PY_TRUFFLE_TYPE("PyCapsule", &PyType_Type, 0, sizeof(PyCapsule));

typedef PyObject* (*capsule_new)(void*, void*, void*);
UPCALL_TYPED_ID(PyCapsule_New, capsule_new);
PyObject *
PyCapsule_New(void *pointer, const char *name, PyCapsule_Destructor destructor) {
    return _jls_PyCapsule_New(pointer, name ? polyglot_from_string(name, SRC_CS) : NULL, destructor);
}

UPCALL_ID(PyCapsule_IsValid);
int
PyCapsule_IsValid(PyObject *o, const char *name) {
    return UPCALL_CEXT_I(_jls_PyCapsule_IsValid, native_to_java(o), name ? polyglot_from_string(name, SRC_CS) : NULL);
}

UPCALL_ID(PyCapsule_GetPointer);
void *
PyCapsule_GetPointer(PyObject *o, const char *name) {
    return UPCALL_CEXT_PTR(_jls_PyCapsule_GetPointer, native_to_java(o), name ? polyglot_from_string(name, SRC_CS) : NULL);
}

UPCALL_ID(PyCapsule_GetName);
const char *
PyCapsule_GetName(PyObject *o) {
    return (const char*)(UPCALL_CEXT_NOCAST(_jls_PyCapsule_GetName, native_to_java(o)));
}

UPCALL_ID(PyCapsule_GetDestructor);
PyCapsule_Destructor
PyCapsule_GetDestructor(PyObject *o) {
    return (PyCapsule_Destructor)(UPCALL_CEXT_PTR(_jls_PyCapsule_GetDestructor, native_to_java(o)));
}

UPCALL_ID(PyCapsule_GetContext);
void *
PyCapsule_GetContext(PyObject *o) {
    return UPCALL_CEXT_PTR(_jls_PyCapsule_GetContext, native_to_java(o));
}

typedef int (*capsule_setpointer)(void*, void*);
UPCALL_TYPED_ID(PyCapsule_SetPointer, capsule_setpointer);
int
PyCapsule_SetPointer(PyObject *o, void *pointer) {
    return _jls_PyCapsule_SetPointer(native_to_java(o), pointer);
}

UPCALL_ID(PyCapsule_SetName);
int
PyCapsule_SetName(PyObject *o, const char *name) {
    return UPCALL_CEXT_I(_jls_PyCapsule_SetName, native_to_java(o), name ? polyglot_from_string(name, SRC_CS) : NULL);
}


typedef int (*capsule_setdestructor)(void*, void*);
UPCALL_TYPED_ID(PyCapsule_SetDestructor, capsule_setdestructor);
int
PyCapsule_SetDestructor(PyObject *o, PyCapsule_Destructor destructor) {
    return _jls_PyCapsule_SetDestructor(native_to_java(o), (intptr_t)destructor);
}

typedef int (*capsule_setctx)(void*, void*);
UPCALL_TYPED_ID(PyCapsule_SetContext, capsule_setctx);
int
PyCapsule_SetContext(PyObject *o, void *context) {
    return _jls_PyCapsule_SetContext(native_to_java(o), (intptr_t)context);
}

UPCALL_ID(PyCapsule_Import);
void *
PyCapsule_Import(const char *name, int no_block) {
    return UPCALL_CEXT_PTR(_jls_PyCapsule_Import, name ? polyglot_from_string(name, SRC_CS) : NULL, no_block);
}

PyTypeObject* getPyCapsuleTypeReference() {
	return &PyCapsule_Type;
}

