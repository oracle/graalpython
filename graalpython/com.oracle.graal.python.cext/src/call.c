/* Copyright (c) 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "capi.h"
#include "pycore_call.h"

inline int is_single_arg(const char* fmt) {
    if (fmt[0] == 0) {
        return 0;
    }
    if (fmt[1] == 0) {
        return 1;
    }
    if (fmt[2] != 0) {
        return 0;
    }
    switch (fmt[1]) {
        case '#':
        case '&':
        case ',':
        case ':':
        case ' ':
        case '\t':
            return 1;
        default:
            return 0;
    }
}

PyObject* PyObject_Call(PyObject* callable, PyObject* args, PyObject* kwargs) {
    return Graal_PyTruffleObject_Call1(callable, args, kwargs, 0);
}

PyObject* PyObject_CallObject(PyObject* callable, PyObject* args) {
    return Graal_PyTruffleObject_Call1(callable, args, NULL, 0);
}

PyObject* PyObject_CallNoArgs(PyObject* callable) {
    return Graal_PyTruffleObject_Call1(callable, NULL, NULL, 0);
}

PyObject* PyObject_CallFunction(PyObject* callable, const char* fmt, ...) {
    if (fmt == NULL || fmt[0] == '\0') {
        return Graal_PyTruffleObject_Call1(callable, NULL, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    PyObject* args = Py_VaBuildValue(fmt, va);
    va_end(va);
    // A special case in CPython for backwards compatibility
    if (is_single_arg(fmt) && PyTuple_Check(args)) {
        return Graal_PyTruffleObject_Call1(callable, args, NULL, 0);
    }
    return Graal_PyTruffleObject_Call1(callable, args, NULL, is_single_arg(fmt));
}

PyObject* _PyObject_CallFunction_SizeT(PyObject* callable, const char* fmt, ...) {
    if (fmt == NULL || fmt[0] == '\0') {
        return Graal_PyTruffleObject_Call1(callable, NULL, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    PyObject* args = _Py_VaBuildValue_SizeT(fmt, va);
    va_end(va);
    // A special case in CPython for backwards compatibility
    if (is_single_arg(fmt) && PyTuple_Check(args)) {
        return Graal_PyTruffleObject_Call1(callable, args, NULL, 0);
    }
    return Graal_PyTruffleObject_Call1(callable, args, NULL, is_single_arg(fmt));
}

PyObject* PyObject_CallMethod(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    if (fmt == NULL || fmt[0] == '\0') {
        return Graal_PyTruffleObject_CallMethod1(object, method, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    args = Py_VaBuildValue(fmt, va);
    va_end(va);
    return Graal_PyTruffleObject_CallMethod1(object, method, args, is_single_arg(fmt));
}

PyObject* _PyObject_CallMethod_SizeT(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    if (fmt == NULL || fmt[0] == '\0') {
        return Graal_PyTruffleObject_CallMethod1(object, method, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    args = _Py_VaBuildValue_SizeT(fmt, va);
    va_end(va);
    return Graal_PyTruffleObject_CallMethod1(object, method, args, is_single_arg(fmt));
}


PyObject * _PyObject_CallMethodIdObjArgs(PyObject *callable, struct _Py_Identifier *name, ...) {
    va_list vargs;
    va_start(vargs, name);
    // the arguments are given as a variable list followed by NULL
    PyObject *result = GraalPyTruffleObject_CallMethodObjArgs(callable, _PyUnicode_FromId(name), &vargs);
    va_end(vargs);
    return result;
}

PyObject* PyObject_CallFunctionObjArgs(PyObject *callable, ...) {
    va_list vargs;
    va_start(vargs, callable);
    // the arguments are given as a variable list followed by NULL
    PyObject *result = GraalPyTruffleObject_CallFunctionObjArgs(callable, &vargs);
    va_end(vargs);
    return result;
}

PyObject* PyObject_CallMethodObjArgs(PyObject *callable, PyObject *name, ...) {
    va_list vargs;
    va_start(vargs, name);
    // the arguments are given as a variable list followed by NULL
    PyObject *result = GraalPyTruffleObject_CallMethodObjArgs(callable, name, &vargs);
    va_end(vargs);
    return result;
}

// Taken from cpython call.c
PyObject *
PyObject_VectorcallMethod(PyObject *name, PyObject *const *args,
                           size_t nargsf, PyObject *kwnames)
{
    assert(name != NULL);
    assert(args != NULL);
    assert(PyVectorcall_NARGS(nargsf) >= 1);

    PyObject *callable = NULL;
    /* Use args[0] as "self" argument */
    int unbound = _PyObject_GetMethod(args[0], name, &callable);
    if (callable == NULL) {
        return NULL;
    }

    if (unbound) {
        /* We must remove PY_VECTORCALL_ARGUMENTS_OFFSET since
         * that would be interpreted as allowing to change args[-1] */
        nargsf &= ~PY_VECTORCALL_ARGUMENTS_OFFSET;
    }
    else {
        /* Skip "self". We can keep PY_VECTORCALL_ARGUMENTS_OFFSET since
         * args[-1] in the onward call is args[0] here. */
        args++;
        nargsf--;
    }
    // TODO remote arg?
    PyObject *result = _PyObject_VectorcallTstate(NULL, callable,
                                                  args, nargsf, kwnames);
    Py_DECREF(callable);
    return result;
}

// Taken from cpython call.c
static void
_PyStack_UnpackDict_Free(PyObject *const *stack, Py_ssize_t nargs,
                         PyObject *kwnames);
static PyObject *const *
_PyStack_UnpackDict(PyObject *const *args, Py_ssize_t nargs,
                    PyObject *kwargs, PyObject **p_kwnames)
{
    assert(nargs >= 0);
    assert(kwargs != NULL);
    assert(PyDict_Check(kwargs));

    Py_ssize_t nkwargs = PyDict_GET_SIZE(kwargs);
    /* Check for overflow in the PyMem_Malloc() call below. The subtraction
     * in this check cannot overflow: both maxnargs and nkwargs are
     * non-negative signed integers, so their difference fits in the type. */
    Py_ssize_t maxnargs = PY_SSIZE_T_MAX / sizeof(args[0]) - 1;
    if (nargs > maxnargs - nkwargs) {
        PyErr_NoMemory();
        return NULL;
    }

    /* Add 1 to support PY_VECTORCALL_ARGUMENTS_OFFSET */
    PyObject **stack = PyMem_Malloc((1 + nargs + nkwargs) * sizeof(args[0]));
    if (stack == NULL) {
        PyErr_NoMemory();
        return NULL;
    }

    PyObject *kwnames = PyTuple_New(nkwargs);
    if (kwnames == NULL) {
        PyMem_Free(stack);
        return NULL;
    }

    stack++;  /* For PY_VECTORCALL_ARGUMENTS_OFFSET */

    /* Copy positional arguments */
    for (Py_ssize_t i = 0; i < nargs; i++) {
        Py_INCREF(args[i]);
        stack[i] = args[i];
    }

    PyObject **kwstack = stack + nargs;
    /* This loop doesn't support lookup function mutating the dictionary
       to change its size. It's a deliberate choice for speed, this function is
       called in the performance critical hot code. */
    Py_ssize_t pos = 0, i = 0;
    PyObject *key, *value;
    unsigned long keys_are_strings = Py_TPFLAGS_UNICODE_SUBCLASS;
    while (PyDict_Next(kwargs, &pos, &key, &value)) {
        keys_are_strings &= Py_TYPE(key)->tp_flags;
        Py_INCREF(key);
        Py_INCREF(value);
        PyTuple_SET_ITEM(kwnames, i, key);
        kwstack[i] = value;
        i++;
    }

    /* keys_are_strings has the value Py_TPFLAGS_UNICODE_SUBCLASS if that
     * flag is set for all keys. Otherwise, keys_are_strings equals 0.
     * We do this check once at the end instead of inside the loop above
     * because it simplifies the deallocation in the failing case.
     * It happens to also make the loop above slightly more efficient. */
    if (!keys_are_strings) {
        PyErr_SetString(PyExc_TypeError,
                         "keywords must be strings");
        _PyStack_UnpackDict_Free(stack, nargs, kwnames);
        return NULL;
    }

    *p_kwnames = kwnames;
    return stack;
}

// Taken from cpython call.c
static void
_PyStack_UnpackDict_Free(PyObject *const *stack, Py_ssize_t nargs,
                         PyObject *kwnames)
{
    Py_ssize_t n = PyTuple_GET_SIZE(kwnames) + nargs;
    for (Py_ssize_t i = 0; i < n; i++) {
        Py_DECREF(stack[i]);
    }
    PyMem_Free((PyObject **)stack - 1);
    Py_DECREF(kwnames);
}

// Taken from cpython call.c
PyObject* PyVectorcall_Call(PyObject *callable, PyObject *tuple, PyObject *kwargs) {
    vectorcallfunc func;

    /* get vectorcallfunc as in PyVectorcall_Function, but without
     * the Py_TPFLAGS_HAVE_VECTORCALL check */
    Py_ssize_t offset = Py_TYPE(callable)->tp_vectorcall_offset;
    if (offset <= 0) {
        PyErr_Format(PyExc_TypeError,
                      "'%.200s' object does not support vectorcall",
                      Py_TYPE(callable)->tp_name);
        return NULL;
    }
    memcpy(&func, (char *) callable + offset, sizeof(func));
    if (func == NULL) {
        PyErr_Format(PyExc_TypeError,
                      "'%.200s' object does not support vectorcall",
                      Py_TYPE(callable)->tp_name);
        return NULL;
    }

    Py_ssize_t nargs = PyTuple_GET_SIZE(tuple);

    /* Fast path for no keywords */
    if (kwargs == NULL || PyDict_GET_SIZE(kwargs) == 0) {
        return func(callable, PyTupleObject_ob_item(tuple), nargs, NULL);
    }

    /* Convert arguments & call */
    PyObject *const *args;
    PyObject *kwnames;
    args = _PyStack_UnpackDict(PyTupleObject_ob_item(tuple), nargs,
                               kwargs, &kwnames);
    if (args == NULL) {
        return NULL;
    }
    PyObject *result = func(callable, args,
                            nargs | PY_VECTORCALL_ARGUMENTS_OFFSET, kwnames);
    _PyStack_UnpackDict_Free(args, nargs, kwnames);

    //return _Py_CheckFunctionResult(tstate, callable, result, NULL);
    return result;
}

// Taken from cpython call.c
PyObject* PyObject_VectorcallDict(PyObject *callable, PyObject *const *args,
                       size_t nargsf, PyObject *kwargs) {
    assert(callable != NULL);

    Py_ssize_t nargs = PyVectorcall_NARGS(nargsf);
    assert(nargs >= 0);
    assert(nargs == 0 || args != NULL);
    assert(kwargs == NULL || PyDict_Check(kwargs));

    vectorcallfunc func = PyVectorcall_Function(callable);
    if (func == NULL) {
        /* Use tp_call instead */
        return _PyObject_MakeTpCall(NULL, callable, args, nargs, kwargs);
    }

    PyObject *res;
    if (kwargs == NULL || PyDict_GET_SIZE(kwargs) == 0) {
        res = func(callable, args, nargsf, NULL);
    }
    else {
        PyObject *kwnames;
        PyObject *const *newargs;
        newargs = _PyStack_UnpackDict(args, nargs,
                                      kwargs, &kwnames);
        if (newargs == NULL) {
            return NULL;
        }
        res = func(callable, newargs,
                   nargs | PY_VECTORCALL_ARGUMENTS_OFFSET, kwnames);
        _PyStack_UnpackDict_Free(newargs, nargs, kwnames);
    }
    return res;
}

vectorcallfunc
PyVectorcall_Function(PyObject *callable)
{
    PyTypeObject *tp;
    Py_ssize_t offset;
    vectorcallfunc ptr;

    assert(callable != NULL);
    tp = Py_TYPE(callable);
    if (!PyType_HasFeature(tp, Py_TPFLAGS_HAVE_VECTORCALL)) {
        return NULL;
    }
    assert(PyCallable_Check(callable));
    offset = tp->tp_vectorcall_offset;
    assert(offset > 0);
    memcpy(&ptr, (char *) callable + offset, sizeof(ptr));
    return ptr;
}

PyObject *
PyObject_Vectorcall(PyObject *callable, PyObject *const *args,
                     size_t nargsf, PyObject *kwnames)
{
    PyThreadState *tstate = PyThreadState_Get();
    return _PyObject_VectorcallTstate(tstate, callable,
                                      args, nargsf, kwnames);
}

/* Same as PyObject_Vectorcall except without keyword arguments */
PyObject *
_PyObject_FastCall(PyObject *func, PyObject *const *args, Py_ssize_t nargs)
{
    return _PyObject_VectorcallTstate(NULL, func, args, (size_t)nargs, NULL);
}

// Taken from CPython call.c
PyObject *
PyObject_CallOneArg(PyObject *func, PyObject *arg)
{
    assert(arg != NULL);
    PyObject *_args[2];
    PyObject **args = _args + 1;  // For PY_VECTORCALL_ARGUMENTS_OFFSET
    args[0] = arg;
    size_t nargsf = 1 | PY_VECTORCALL_ARGUMENTS_OFFSET;
    return _PyObject_VectorcallTstate(NULL, func, args, nargsf, NULL);
}

