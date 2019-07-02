/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include "capi.h"

PyObject* PyObject_SelfIter(PyObject* obj) {
    return obj;
}

PyObject* PyType_GenericNew(PyTypeObject* cls, PyObject* args, PyObject* kwds) {
    PyObject* newInstance;
    newInstance = cls->tp_alloc(cls, 0);
    newInstance->ob_refcnt = 0;
    Py_TYPE(newInstance) = cls;
    return newInstance;
}

/*
None is a non-NULL undefined value.
There is (and should be!) no way to create other objects of this type,
so there is exactly one (which is indestructible, by the way).
*/

/* ARGSUSED */
static PyObject *
none_repr(PyObject *op)
{
    return PyUnicode_FromString("None");
}

/* ARGUSED */
static void
none_dealloc(PyObject* ignore)
{
    /* This should never get called, but we also don't want to SEGV if
     * we accidentally decref None out of existence.
     */
    Py_FatalError("deallocating None");
}

static PyObject *
none_new(PyTypeObject *type, PyObject *args, PyObject *kwargs)
{
    Py_RETURN_NONE;
}

static int
none_bool(PyObject *v)
{
    return 0;
}

static PyNumberMethods none_as_number = {
    0,                          /* nb_add */
    0,                          /* nb_subtract */
    0,                          /* nb_multiply */
    0,                          /* nb_remainder */
    0,                          /* nb_divmod */
    0,                          /* nb_power */
    0,                          /* nb_negative */
    0,                          /* nb_positive */
    0,                          /* nb_absolute */
    (inquiry)none_bool,         /* nb_bool */
    0,                          /* nb_invert */
    0,                          /* nb_lshift */
    0,                          /* nb_rshift */
    0,                          /* nb_and */
    0,                          /* nb_xor */
    0,                          /* nb_or */
    0,                          /* nb_int */
    0,                          /* nb_reserved */
    0,                          /* nb_float */
    0,                          /* nb_inplace_add */
    0,                          /* nb_inplace_subtract */
    0,                          /* nb_inplace_multiply */
    0,                          /* nb_inplace_remainder */
    0,                          /* nb_inplace_power */
    0,                          /* nb_inplace_lshift */
    0,                          /* nb_inplace_rshift */
    0,                          /* nb_inplace_and */
    0,                          /* nb_inplace_xor */
    0,                          /* nb_inplace_or */
    0,                          /* nb_floor_divide */
    0,                          /* nb_true_divide */
    0,                          /* nb_inplace_floor_divide */
    0,                          /* nb_inplace_true_divide */
    0,                          /* nb_index */
};

PyTypeObject _PyNone_Type = PY_TRUFFLE_TYPE("NoneType", &PyType_Type, Py_TPFLAGS_DEFAULT, 0);

PyObject _Py_NoneStruct = {
  _PyObject_EXTRA_INIT
  1, &_PyNone_Type
};

PyTypeObject _PyNotImplemented_Type = PY_TRUFFLE_TYPE("NotImplementedType", &PyType_Type, Py_TPFLAGS_DEFAULT, 0);

PyObject _Py_NotImplementedStruct = {
    _PyObject_EXTRA_INIT
    1, &_PyNotImplemented_Type
};

PyObject* PyType_GenericAlloc(PyTypeObject* cls, Py_ssize_t nitems) {
	Py_ssize_t size = cls->tp_basicsize + cls->tp_itemsize * nitems;
    PyObject* newObj = (PyObject*)PyObject_Malloc(size);
    if(cls->tp_dictoffset) {
    	*((PyObject **) ((char *)newObj + cls->tp_dictoffset)) = NULL;
    }
    Py_TYPE(newObj) = cls;
    if (nitems > 0) {
        ((PyVarObject*)newObj)->ob_size = nitems;
    }
    return newObj;
}

int PyObject_GenericInit(PyObject* self, PyObject* args, PyObject* kwds) {
    return self;
}

void* PyObject_Malloc(size_t size) {
    return calloc(size, 1);
}

void* PyObject_Realloc(void *ptr, size_t new_size) {
	return realloc(ptr, new_size);
}

void PyObject_Free(void* ptr) {
    free(ptr);
}

UPCALL_ID(PyObject_Size);
Py_ssize_t PyObject_Size(PyObject *o) {
    return UPCALL_CEXT_L(_jls_PyObject_Size, native_to_java(o));
}

UPCALL_ID(PyObject_Str);
PyObject* PyObject_Str(PyObject* o) {
    return UPCALL_CEXT_O(_jls_PyObject_Str, native_to_java(o));
}

PyObject* PyObject_ASCII(PyObject* o) {
    return UPCALL_O(PY_BUILTIN, polyglot_from_string("ascii", SRC_CS), native_to_java(o));
}

UPCALL_ID(PyObject_Repr);
PyObject* PyObject_Repr(PyObject* o) {
    return UPCALL_CEXT_O(_jls_PyObject_Repr, native_to_java(o));
}

UPCALL_ID(PyObject_Call);
PyObject* PyObject_Call(PyObject* callable, PyObject* args, PyObject* kwargs) {
    if (kwargs == NULL) {
        kwargs = PyDict_New();
    }
    return UPCALL_CEXT_O(_jls_PyObject_Call, native_to_java(callable), native_to_java(args), native_to_java(kwargs));
}

PyObject* PyObject_CallObject(PyObject* callable, PyObject* args) {
    return PyObject_Call(callable, args, PyDict_New());
}

// (tfel): this is used a couple of times in this file only, for now
#define CALL_WITH_VARARGS(retval, funcname, skipN, ...)                 \
    switch (polyglot_get_arg_count() - skipN) {                         \
    case 0:                                                             \
        retval = funcname(__VA_ARGS__); break;                          \
    case 1:                                                             \
        retval = funcname(__VA_ARGS__, polyglot_get_arg(skipN)); break; \
    case 2:                                                             \
        retval = funcname(__VA_ARGS__, polyglot_get_arg(skipN), polyglot_get_arg(skipN + 1)); break; \
    case 3:                                                             \
        retval = funcname(__VA_ARGS__, polyglot_get_arg(skipN), polyglot_get_arg(skipN + 1), polyglot_get_arg(skipN + 2)); break; \
    case 4:                                                             \
        retval = funcname(__VA_ARGS__, polyglot_get_arg(skipN), polyglot_get_arg(skipN + 1), polyglot_get_arg(skipN + 2), polyglot_get_arg(skipN + 3)); break; \
    case 5:                                                             \
        retval = funcname(__VA_ARGS__, polyglot_get_arg(skipN), polyglot_get_arg(skipN + 1), polyglot_get_arg(skipN + 2), polyglot_get_arg(skipN + 3), polyglot_get_arg(skipN + 4)); break; \
    case 6:                                                             \
        retval = funcname(__VA_ARGS__, polyglot_get_arg(skipN), polyglot_get_arg(skipN + 1), polyglot_get_arg(skipN + 2), polyglot_get_arg(skipN + 3), polyglot_get_arg(skipN + 4), polyglot_get_arg(skipN + 5)); break; \
    case 7:                                                             \
        retval = funcname(__VA_ARGS__, polyglot_get_arg(skipN), polyglot_get_arg(skipN + 1), polyglot_get_arg(skipN + 2), polyglot_get_arg(skipN + 3), polyglot_get_arg(skipN + 4), polyglot_get_arg(skipN + 5), polyglot_get_arg(skipN + 6)); break; \
    case 8:                                                             \
        retval = funcname(__VA_ARGS__, polyglot_get_arg(skipN), polyglot_get_arg(skipN + 1), polyglot_get_arg(skipN + 2), polyglot_get_arg(skipN + 3), polyglot_get_arg(skipN + 4), polyglot_get_arg(skipN + 5), polyglot_get_arg(skipN + 6), polyglot_get_arg(skipN + 7)); break; \
    case 9:                                                             \
        retval = funcname(__VA_ARGS__, polyglot_get_arg(skipN), polyglot_get_arg(skipN + 1), polyglot_get_arg(skipN + 2), polyglot_get_arg(skipN + 3), polyglot_get_arg(skipN + 4), polyglot_get_arg(skipN + 5), polyglot_get_arg(skipN + 6), polyglot_get_arg(skipN + 7), polyglot_get_arg(skipN + 8)); break; \
    default:                                                            \
        fprintf(stderr, "Too many arguments passed through varargs: %d", polyglot_get_arg_count() - skipN); \
    }

PyObject* PyObject_CallFunction(PyObject* callable, const char* fmt, ...) {
    PyObject* args;
    CALL_WITH_VARARGS(args, Py_BuildValue, 2, fmt);
    if (strlen(fmt) < 2) {
        PyObject* singleArg = args;
        args = PyTuple_New(strlen(fmt));
        if (strlen(fmt) == 1) {
            PyTuple_SetItem(args, 0, singleArg);
        }
    }
    return PyObject_CallObject(callable, args);
}

PyObject* PyObject_CallFunctionObjArgs(PyObject *callable, ...) {
    // the arguments are given as a variable list followed by NULL
    PyObject* args = PyTuple_New(polyglot_get_arg_count() - 2);
    for (int i = 1; i < polyglot_get_arg_count() - 1; i++) {
        PyTuple_SetItem(args, i - 1, polyglot_get_arg(i));
    }
    return PyObject_CallObject(callable, args);
}

UPCALL_ID(PyObject_CallMethod);
PyObject* PyObject_CallMethod(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    CALL_WITH_VARARGS(args, Py_BuildValue, 3, fmt);
    return UPCALL_CEXT_O(_jls_PyObject_CallMethod, native_to_java(object), polyglot_from_string(method, SRC_CS), native_to_java(args));
}

PyObject* _PyObject_CallMethod_SizeT(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    CALL_WITH_VARARGS(args, Py_BuildValue, 3, fmt);
    return UPCALL_CEXT_O(_jls_PyObject_CallMethod, native_to_java(object), polyglot_from_string(method, SRC_CS), native_to_java(args));
}

PyObject * _PyObject_FastCallDict(PyObject *func, PyObject *const *args, Py_ssize_t nargs, PyObject *kwargs) {
	PyObject* targs = PyTuple_New(nargs);
	Py_ssize_t i;
	for(i=0; i < nargs; i++) {
		PyTuple_SetItem(targs, i, args[i]);
	}
    if (kwargs == NULL) {
        kwargs = PyDict_New();
    }
    return UPCALL_CEXT_O(_jls_PyObject_Call, native_to_java(func), native_to_java(targs), native_to_java(kwargs));
}

PyObject* PyObject_Type(PyObject* obj) {
    return UPCALL_O(PY_BUILTIN, polyglot_from_string("type", SRC_CS), native_to_java(obj));
}

PyObject* PyObject_GetItem(PyObject* obj, PyObject* key) {
    return UPCALL_O(native_to_java(obj), polyglot_from_string("__getitem__", SRC_CS), native_to_java(key));
}

UPCALL_ID(PyObject_SetItem);
int PyObject_SetItem(PyObject* obj, PyObject* key, PyObject* value) {
    return UPCALL_CEXT_I(_jls_PyObject_SetItem, native_to_java(obj), native_to_java(key), native_to_java(value));
}

PyObject* PyObject_Format(PyObject* obj, PyObject* spec) {
    return UPCALL_O(native_to_java(obj), polyglot_from_string("__format__", SRC_CS), native_to_java(spec));
}

PyObject* PyObject_GetIter(PyObject* obj) {
    return UPCALL_O(PY_BUILTIN, polyglot_from_string("iter", SRC_CS), native_to_java(obj));
}

UPCALL_ID(PyObject_IsInstance);
int PyObject_IsInstance(PyObject* obj, PyObject* typ) {
    return UPCALL_CEXT_I(_jls_PyObject_IsInstance, native_to_java(obj), native_to_java(typ));
}

UPCALL_ID(PyObject_IsSubclass);
int PyObject_IsSubclass(PyObject *derived, PyObject *cls) {
    return UPCALL_CEXT_I(_jls_PyObject_IsSubclass, native_to_java(derived), native_to_java(cls));
}

UPCALL_ID(PyObject_AsFileDescriptor);
int PyObject_AsFileDescriptor(PyObject* obj) {
    return UPCALL_CEXT_I(_jls_PyObject_AsFileDescriptor, native_to_java(obj));
}

UPCALL_ID(PyTruffle_GetBuiltin);
int PyObject_Print(PyObject* object, FILE* fd, int flags) {
    void *openFunc, *args, *kwargs;
    void *printfunc, *printargs, *printkwargs;
    void *file;

    openFunc = UPCALL_CEXT_O(_jls_PyTruffle_GetBuiltin, polyglot_from_string("open", SRC_CS));
    args = PyTuple_New(1);
    int f = fileno(fd);
    PyTuple_SetItem(args, 0, PyLong_FromLong(f));
    kwargs = PyDict_New();
    int buffering = 0;
    PyDict_SetItemString(kwargs, "buffering", PyLong_FromLong(buffering));
    PyDict_SetItemString(kwargs, "mode", polyglot_from_string("wb", SRC_CS));
    file = PyObject_Call(openFunc, args, kwargs);

    printfunc = UPCALL_CEXT_O(_jls_PyTruffle_GetBuiltin, polyglot_from_string("print", SRC_CS));
    printargs = PyTuple_New(1);
    PyTuple_SetItem(printargs, 0, object);
    printkwargs = PyDict_New();
    PyDict_SetItemString(printkwargs, "file", file);
    PyObject_Call(printfunc, printargs, printkwargs);
    return 0;
}

UPCALL_ID(PyObject_GetAttr);
PyObject* PyObject_GetAttrString(PyObject* obj, const char* attr) {
    return UPCALL_CEXT_O(_jls_PyObject_GetAttr, native_to_java(obj), polyglot_from_string(attr, SRC_CS));
}

UPCALL_ID(PyObject_SetAttr);
int PyObject_SetAttrString(PyObject* obj, const char* attr, PyObject* value) {
    return UPCALL_CEXT_I(_jls_PyObject_SetAttr, native_to_java(obj), polyglot_from_string(attr, SRC_CS), native_to_java(value));
}

UPCALL_ID(PyObject_HasAttr);
int PyObject_HasAttr(PyObject* obj, PyObject* attr) {
    return UPCALL_CEXT_I(_jls_PyObject_HasAttr, native_to_java(obj), native_to_java(attr));
}

int PyObject_HasAttrString(PyObject* obj, const char* attr) {
    return UPCALL_CEXT_I(_jls_PyObject_HasAttr, native_to_java(obj), polyglot_from_string(attr, SRC_CS));
}

PyObject* PyObject_GetAttr(PyObject* obj, PyObject* attr) {
    return UPCALL_CEXT_O(_jls_PyObject_GetAttr, native_to_java(obj), native_to_java(attr));
}

PyObject* PyObject_GenericGetAttr(PyObject* obj, PyObject* attr) {
    return PyObject_GetAttr(obj, attr);
}

int PyObject_SetAttr(PyObject* obj, PyObject* attr, PyObject* value) {
    return PyObject_SetAttrString(obj, as_char_pointer(attr), value);
}

int PyObject_GenericSetAttr(PyObject* obj, PyObject* attr, PyObject* value) {
    return PyObject_SetAttr(obj, attr, value);
}

Py_hash_t PyObject_Hash(PyObject* obj) {
    return UPCALL_I(PY_BUILTIN, polyglot_from_string("hash", SRC_CS), native_to_java(obj));
}

UPCALL_ID(PyObject_HashNotImplemented);
Py_hash_t PyObject_HashNotImplemented(PyObject* obj) {
    UPCALL_CEXT_VOID(_jls_PyObject_HashNotImplemented, native_to_java(obj));
    return -1;
}

UPCALL_ID(PyObject_IsTrue);
int PyObject_IsTrue(PyObject* obj) {
    return UPCALL_CEXT_I(_jls_PyObject_IsTrue, native_to_java(obj));
}

int PyObject_Not(PyObject* obj) {
    return PyObject_IsTrue(obj) ? 0 : 1;
}

UPCALL_ID(PyObject_RichCompare);
PyObject * PyObject_RichCompare(PyObject *v, PyObject *w, int op) {
    return UPCALL_CEXT_O(_jls_PyObject_RichCompare, native_to_java(v), native_to_java(w), op);
}

int PyObject_RichCompareBool(PyObject *v, PyObject *w, int op) {
    PyObject* res = PyObject_RichCompare(v, w, op);
    if (res == NULL) {
        return -1;
    } else {
        return PyObject_IsTrue(res);
    }
}

PyObject* _PyObject_New(PyTypeObject *tp) {
    PyObject *op = (PyObject*)PyObject_MALLOC(_PyObject_SIZE(tp));
    if (op == NULL) {
        return PyErr_NoMemory();
    }
    return PyObject_INIT(op, tp);
}

void PyObject_GC_Track(void *tp) {
}

PyObject* _PyObject_GC_New(PyTypeObject *tp) {
    return _PyObject_New(tp);
}

PyVarObject* _PyObject_GC_NewVar(PyTypeObject *tp, Py_ssize_t nitems) {
    return _PyObject_NewVar(tp, nitems);
}

PyVarObject *
_PyObject_NewVar(PyTypeObject *tp, Py_ssize_t nitems)
{
    PyVarObject *op;
    const size_t size = _PyObject_VAR_SIZE(tp, nitems);
    op = (PyVarObject *) PyObject_MALLOC(size);
    if (op == NULL)
        return (PyVarObject *)PyErr_NoMemory();
    return PyObject_INIT_VAR(op, tp, nitems);
}

PyObject* PyObject_Init(PyObject *op, PyTypeObject *tp) {
    if (op == NULL) {
        return PyErr_NoMemory();
    }
    Py_TYPE(op) = tp;
    _Py_NewReference(op);
    return op;
}

// taken from CPython 3.6.4 "Objects/object.c"
PyVarObject * PyObject_InitVar(PyVarObject *op, PyTypeObject *tp, Py_ssize_t size) {
    if (op == NULL) {
        return (PyVarObject *) PyErr_NoMemory();
    }
    /* Any changes should be reflected in PyObject_INIT_VAR */
    op->ob_size = size;
    Py_TYPE(op) = tp;
    _Py_NewReference((PyObject *)op);
    return op;
}

int PyCallable_Check(PyObject *x) {
	return UPCALL_I(PY_BUILTIN, polyglot_from_string("callable", SRC_CS), native_to_java(x));
}

PyObject * PyObject_Dir(PyObject *obj) {
	return UPCALL_O(PY_BUILTIN, polyglot_from_string("dir", SRC_CS), native_to_java(obj));
}
