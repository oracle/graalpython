/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "pycore_pymem.h"
#include "pycore_object.h"

PyObject* PyObject_SelfIter(PyObject* obj) {
    Py_INCREF(obj);
    return obj;
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

PyTypeObject _PyNotImplemented_Type = PY_TRUFFLE_TYPE("NotImplementedType", &PyType_Type, Py_TPFLAGS_DEFAULT, 0);

int PyObject_GenericInit(PyObject* self, PyObject* args, PyObject* kwds) {
    return self;
}

typedef void (*object_dump_fun_t)(PyObject *);
UPCALL_TYPED_ID(_PyObject_Dump, object_dump_fun_t);
void _PyObject_Dump(PyObject* op) {
    _jls__PyObject_Dump(op);
}

UPCALL_ID(PyObject_Str);
PyObject* PyObject_Str(PyObject* o) {
	if (o == NULL)
		return PyUnicode_FromString("<NULL>");
    return UPCALL_CEXT_O(_jls_PyObject_Str, native_to_java(o));
}

PyObject* PyObject_ASCII(PyObject* o) {
	if (o == NULL)
		return PyUnicode_FromString("<NULL>");
    return UPCALL_O(PY_BUILTIN, polyglot_from_string("ascii", SRC_CS), native_to_java(o));
}

UPCALL_ID(PyObject_Repr);
PyObject* PyObject_Repr(PyObject* o) {
	if (o == NULL)
		return PyUnicode_FromString("<NULL>");
    return UPCALL_CEXT_O(_jls_PyObject_Repr, native_to_java(o));
}

#define IS_SINGLE_ARG(_fmt) ((_fmt[0]) != '\0' && (_fmt[1]) == '\0')

typedef PyObject *(*call_fun_t)(PyObject *, PyObject *, PyObject *, int32_t);
UPCALL_TYPED_ID(PyObject_Call, call_fun_t);
PyObject* PyObject_Call(PyObject* callable, PyObject* args, PyObject* kwargs) {
	return _jls_PyObject_Call(native_to_java(callable), native_to_java(args), native_to_java(kwargs), 0);
}

PyObject* PyObject_CallObject(PyObject* callable, PyObject* args) {
	return _jls_PyObject_Call(native_to_java(callable), native_to_java(args), NULL, 0);
}

PyObject* PyObject_CallFunction(PyObject* callable, const char* fmt, ...) {
    if (fmt == NULL || fmt[0] == '\0') {
	    return _jls_PyObject_Call(native_to_java(callable), NULL, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    PyObject* args = Py_VaBuildValue(fmt, va);
    va_end(va);
	return _jls_PyObject_Call(native_to_java(callable), native_to_java(args), NULL, IS_SINGLE_ARG(fmt));
}

PyObject* _PyObject_CallFunction_SizeT(PyObject* callable, const char* fmt, ...) {
    if (fmt == NULL || fmt[0] == '\0') {
	    return _jls_PyObject_Call(native_to_java(callable), NULL, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    PyObject* args = Py_VaBuildValue(fmt, va);
    va_end(va);
	return _jls_PyObject_Call(native_to_java(callable), native_to_java(args), NULL, IS_SINGLE_ARG(fmt));
}

typedef PyObject *(*call_fun_obj_args_t)(PyObject *, void *);
UPCALL_TYPED_ID(PyObject_CallFunctionObjArgs, call_fun_obj_args_t);
PyObject* PyObject_CallFunctionObjArgs(PyObject *callable, ...) {
    va_list vargs;
    va_start(vargs, callable);
    // the arguments are given as a variable list followed by NULL
    PyObject *result = _jls_PyObject_CallFunctionObjArgs(native_to_java(callable), &vargs);
    va_end(vargs);
    return result;
}

typedef PyObject *(*call_method_t)(PyObject *, void *, void *, int32_t);
UPCALL_TYPED_ID(PyObject_CallMethod, call_method_t);
PyObject* PyObject_CallMethod(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    if (fmt == NULL || fmt[0] == '\0') {
        return _jls_PyObject_CallMethod(native_to_java(object), polyglot_from_string(method, SRC_CS), NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    args = Py_VaBuildValue(fmt, va);
    va_end(va);
    return _jls_PyObject_CallMethod(native_to_java(object), polyglot_from_string(method, SRC_CS), native_to_java(args), IS_SINGLE_ARG(fmt));
}

typedef PyObject *(*call_meth_obj_args_t)(PyObject *, void *, void *);
UPCALL_TYPED_ID(PyObject_CallMethodObjArgs, call_meth_obj_args_t);
PyObject* PyObject_CallMethodObjArgs(PyObject *callable, PyObject *name, ...) {
    va_list vargs;
    va_start(vargs, name);
    // the arguments are given as a variable list followed by NULL
    PyObject *result = _jls_PyObject_CallMethodObjArgs(native_to_java(callable), native_to_java(name), &vargs);
    va_end(vargs);
    return result;
}

PyObject* _PyObject_CallMethod_SizeT(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    if (fmt == NULL || fmt[0] == '\0') {
        return _jls_PyObject_CallMethod(native_to_java(object), polyglot_from_string(method, SRC_CS), NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    args = Py_VaBuildValue(fmt, va);
    va_end(va);
    return _jls_PyObject_CallMethod(native_to_java(object), polyglot_from_string(method, SRC_CS), native_to_java(args), IS_SINGLE_ARG(fmt));
}

typedef PyObject *(*make_tp_call_func)(PyObject *, void *, PyObject *, void *);
UPCALL_TYPED_ID(_PyObject_MakeTpCall, make_tp_call_func);
PyObject* _PyObject_MakeTpCall(PyThreadState *tstate, PyObject *callable,
                     PyObject *const *args, Py_ssize_t nargs,
                     PyObject *keywords) {
    void* kwvalues = NULL;
    if (keywords != NULL && PyTuple_Check(keywords)) {
        kwvalues = polyglot_from_PyObjectPtr_array(args + nargs, PyTuple_GET_SIZE(keywords));
    }
    return _jls__PyObject_MakeTpCall(native_to_java(callable), polyglot_from_PyObjectPtr_array(args, nargs), native_to_java(keywords), kwvalues);
}

PyObject* PyObject_Type(PyObject* obj) {
    return UPCALL_O(PY_BUILTIN, polyglot_from_string("type", SRC_CS), native_to_java(obj));
}

typedef PyObject* (*getitem_fun_t)(PyObject*, PyObject*);
UPCALL_TYPED_ID(PyObject_GetItem, getitem_fun_t);
PyObject* PyObject_GetItem(PyObject* obj, PyObject* key) {
    return _jls_PyObject_GetItem(native_to_java(obj), native_to_java(key));
}

UPCALL_ID(PyObject_SetItem);
int PyObject_SetItem(PyObject* obj, PyObject* key, PyObject* value) {
    return UPCALL_CEXT_I(_jls_PyObject_SetItem, native_to_java(obj), native_to_java(key), native_to_java(value));
}

UPCALL_ID(PyObject_DelItem);
int PyObject_DelItem(PyObject *o, PyObject *key) {
	return UPCALL_CEXT_I(_jls_PyObject_DelItem, native_to_java(o), native_to_java(key));
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

// Taken from CPython
int PyObject_Print(PyObject *op, FILE *fp, int flags)
{
	op = native_pointer_to_java(op);
    int ret = 0;
    clearerr(fp); /* Clear any previous error condition */
    if (op == NULL) {
        Py_BEGIN_ALLOW_THREADS
        fprintf(fp, "<nil>");
        Py_END_ALLOW_THREADS
    }
    else {
        if (op->ob_refcnt <= 0) {
            /* XXX(twouters) cast refcount to long until %zd is
               universally available */
            Py_BEGIN_ALLOW_THREADS
            fprintf(fp, "<refcnt %ld at %p>",
                (long)op->ob_refcnt, (void *)op);
            Py_END_ALLOW_THREADS
        }
        else {
            PyObject *s;
            if (flags & Py_PRINT_RAW)
                s = PyObject_Str(op);
            else
                s = PyObject_Repr(op);
            if (s == NULL)
                ret = -1;
            else if (PyBytes_Check(s)) {
                fwrite(PyBytes_AS_STRING(s), 1,
                       PyBytes_GET_SIZE(s), fp);
            }
            else if (PyUnicode_Check(s)) {
                PyObject *t;
                t = PyUnicode_AsEncodedString(s, "utf-8", "backslashreplace");
                if (t == NULL) {
                    ret = -1;
                }
                else {
                    fwrite(PyBytes_AS_STRING(t), 1,
                           PyBytes_GET_SIZE(t), fp);
                    Py_DECREF(t);
                }
            }
            else {
                PyErr_Format(PyExc_TypeError,
                             "str() or repr() returned '%.100s'",
                             Py_TYPE(s)->tp_name);
                ret = -1;
            }
            Py_XDECREF(s);
        }
    }
    if (ret == 0) {
        if (ferror(fp)) {
            PyErr_SetFromErrno(PyExc_OSError);
            clearerr(fp);
            ret = -1;
        }
    }
    return ret;
}

// taken from CPython "Objects/object.c"
PyObject * PyObject_GetAttrString(PyObject *v, const char *name) {
	v = native_pointer_to_java(v);
    PyObject *w, *res;

    if (Py_TYPE(v)->tp_getattr != NULL) {
        return (*Py_TYPE(v)->tp_getattr)(v, (char*)name);
    }
    w = PyUnicode_FromString(name);
    if (w == NULL) {
        return NULL;
    }
    res = PyObject_GetAttr(v, w);
    return res;
}


// taken from CPython "Objects/object.c"
int PyObject_SetAttrString(PyObject *v, const char *name, PyObject *w) {
	v = native_pointer_to_java(v);
    PyObject *s;
    int res;
    PyTypeObject *type = Py_TYPE(v);

    if (type->tp_setattr != NULL) {
        return (*type->tp_setattr)(v, (char*)name, w);
    }
    // TODO(fa): CPython interns strings; verify if that makes sense for us as well
    // s = PyUnicode_InternFromString(name);
    s = PyUnicode_FromString(name);
    if (s == NULL) {
        return -1;
    }
    res = PyObject_SetAttr(v, s, w);
    return res;
}

UPCALL_ID(PyObject_HasAttr);
int PyObject_HasAttr(PyObject* obj, PyObject* attr) {
    return UPCALL_CEXT_I(_jls_PyObject_HasAttr, native_to_java(obj), native_to_java(attr));
}

int PyObject_HasAttrString(PyObject* obj, const char* attr) {
    return UPCALL_CEXT_I(_jls_PyObject_HasAttr, native_to_java(obj), polyglot_from_string(attr, SRC_CS));
}

/* Note: We must implement this in native because it might happen that this function is used on an
   uninitialized type which means that a managed attribute lookup won't work. */
// taken from CPython "Objects/object.c"
PyObject * PyObject_GetAttr(PyObject *v, PyObject *name) {
	v = native_pointer_to_java(v);
	name = native_pointer_to_java(name);
    PyTypeObject *tp = Py_TYPE(v);

    if (!PyUnicode_Check(name)) {
        PyErr_Format(PyExc_TypeError,
                     "attribute name must be string, not '%.200s'",
                     name->ob_type->tp_name);
        return NULL;
    }
    if (tp->tp_getattro != NULL) {
        return (*tp->tp_getattro)(v, name);
    }
    if (tp->tp_getattr != NULL) {
        const char *name_str = PyUnicode_AsUTF8(name);
        if (name_str == NULL) {
            return NULL;
        }
        return (*tp->tp_getattr)(v, (char *)name_str);
    }
    PyErr_Format(PyExc_AttributeError,
                 "'%.50s' object has no attribute '%U'",
                 tp->tp_name, name);
    return NULL;
}

// taken from CPython "Objects/object.c"
int _PyObject_LookupAttr(PyObject *v, PyObject *name, PyObject **result)
{
	v = native_pointer_to_java(v);
	name = native_pointer_to_java(name);
    PyTypeObject *tp = Py_TYPE(v);

    if (!PyUnicode_Check(name)) {
        PyErr_Format(PyExc_TypeError,
                     "attribute name must be string, not '%.200s'",
					 Py_TYPE(name)->tp_name);
        *result = NULL;
        return -1;
    }

    /* Truffle change: this fast path is only applicable to cpython
    if (tp->tp_getattro == PyObject_GenericGetAttr) {
        *result = _PyObject_GenericGetAttrWithDict(v, name, NULL, 1);
        if (*result != NULL) {
            return 1;
        }
        if (PyErr_Occurred()) {
            return -1;
        }
        return 0;
    }
    */
    if (tp->tp_getattro != NULL) {
        *result = (*tp->tp_getattro)(v, name);
    }
    else if (tp->tp_getattr != NULL) {
        const char *name_str = PyUnicode_AsUTF8(name);
        if (name_str == NULL) {
            *result = NULL;
            return -1;
        }
        *result = (*tp->tp_getattr)(v, (char *)name_str);
    }
    else {
        *result = NULL;
        return 0;
    }

    if (*result != NULL) {
        return 1;
    }
    if (!PyErr_ExceptionMatches(PyExc_AttributeError)) {
        return -1;
    }
    PyErr_Clear();
    return 0;
}

// taken from CPython "Objects/object.c"
int _PyObject_LookupAttrId(PyObject *v, _Py_Identifier *name, PyObject **result)
{
    PyObject *oname = _PyUnicode_FromId(name); /* borrowed */
    if (!oname) {
        *result = NULL;
        return -1;
    }
    return  _PyObject_LookupAttr(v, oname, result);
}

UPCALL_ID(PyObject_GenericGetAttr);
PyObject* PyObject_GenericGetAttr(PyObject* obj, PyObject* attr) {
	obj = native_pointer_to_java(obj);
    PyTypeObject *tp = Py_TYPE(obj);
    if (tp->tp_dict == NULL && PyType_Ready(tp) < 0) {
    	return NULL;
    }
	return UPCALL_CEXT_O(_jls_PyObject_GenericGetAttr, native_to_java(obj), native_to_java(attr));
}

/* Note: We must implement this in native because it might happen that this function is used on an
   unitialized type which means that a managed attribute lookup won't work. */
// taken from CPython "Objects/object.c"
int PyObject_SetAttr(PyObject *v, PyObject *name, PyObject *value) {
	v = native_pointer_to_java(v);
	name = native_pointer_to_java(name);
    PyTypeObject *tp = Py_TYPE(v);
    int err;

    if (!PyUnicode_Check(name)) {
        PyErr_Format(PyExc_TypeError,
                     "attribute name must be string, not '%.200s'",
					 Py_TYPE(name)->tp_name);
        return -1;
    }

    // TODO(fa): CPython interns strings; verify if that makes sense for us as well
    // PyUnicode_InternInPlace(&name);
    if (tp->tp_setattro != NULL) {
        err = (*tp->tp_setattro)(v, name, value);
        return err;
    }
    if (tp->tp_setattr != NULL) {
        const char *name_str = PyUnicode_AsUTF8(name);
        if (name_str == NULL)
            return -1;
        err = (*tp->tp_setattr)(v, (char *)name_str, value);
        return err;
    }
    if (tp->tp_getattr == NULL && tp->tp_getattro == NULL)
        PyErr_Format(PyExc_TypeError,
                     "'%.100s' object has no attributes "
                     "(%s .%U)",
                     tp->tp_name,
                     value==NULL ? "del" : "assign to",
                     name);
    else
        PyErr_Format(PyExc_TypeError,
                     "'%.100s' object has only read-only attributes "
                     "(%s .%U)",
                     tp->tp_name,
                     value==NULL ? "del" : "assign to",
                     name);
    return -1;
}

// taken from CPython "Objects/object.c"
int _PyObject_SetAttrId(PyObject *v, _Py_Identifier *name, PyObject *w)
{
    int result;
    PyObject *oname = _PyUnicode_FromId(name); /* borrowed */
    if (!oname)
        return -1;
    result = PyObject_SetAttr(v, oname, w);
    return result;
}

UPCALL_ID(PyObject_GenericSetAttr);
int PyObject_GenericSetAttr(PyObject* obj, PyObject* attr, PyObject* value) {
	obj = native_pointer_to_java(obj);
    PyTypeObject *tp = Py_TYPE(obj);
    if (tp->tp_dict == NULL && PyType_Ready(tp) < 0) {
        return -1;
    }
	return (int) UPCALL_CEXT_L(_jls_PyObject_GenericSetAttr, native_to_java(obj), native_to_java(attr), native_to_java(value));
}

Py_hash_t PyObject_Hash(PyObject* obj) {
    return UPCALL_L(PY_BUILTIN, polyglot_from_string("hash", SRC_CS), native_to_java(obj));
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
	(void) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_GC_Track", tp);
}

void PyObject_GC_Del(void *tp) {
	PyObject_Free(tp);
}


void PyObject_GC_UnTrack(void *tp) {
	(void) polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_GC_Untrack", tp);
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

void Py_IncRef(PyObject *o) {
    Py_XINCREF(o);
}

void Py_DecRef(PyObject *o) {
    Py_XDECREF(o);
}

PyObject* PyObject_Init(PyObject *op, PyTypeObject *tp) {
	op = native_pointer_to_java(op);
    if (op == NULL) {
        return PyErr_NoMemory();
    }
    Py_SET_TYPE(op, tp);
    /* TOOD(fa): properly set HEAPTYPE flag */
    /*
    if (PyType_GetFlags(tp) & Py_TPFLAGS_HEAPTYPE) {
        Py_INCREF(tp);
    }
    */
    Py_INCREF(tp);
    _Py_NewReference(op);
    return op;
}

// taken from CPython "Objects/object.c"
PyVarObject * PyObject_InitVar(PyVarObject *op, PyTypeObject *tp, Py_ssize_t size) {
	op = native_pointer_to_java(op);
    if (op == NULL) {
        return (PyVarObject *) PyErr_NoMemory();
    }
    /* Any changes should be reflected in PyObject_INIT_VAR */
    op->ob_size = size;
    Py_SET_TYPE(op, tp);
    _Py_NewReference((PyObject *)op);
    return op;
}

int PyCallable_Check(PyObject *x) {
	return UPCALL_I(PY_BUILTIN, polyglot_from_string("callable", SRC_CS), native_to_java(x));
}

PyObject * PyObject_Dir(PyObject *obj) {
	return UPCALL_O(PY_BUILTIN, polyglot_from_string("dir", SRC_CS), native_to_java(obj));
}

// taken from CPython "Objects/object.c"
PyObject * _PyObject_GetAttrId(PyObject *v, _Py_Identifier *name) {
    PyObject *result;
    PyObject *oname = _PyUnicode_FromId(name);
    if (!oname)
        return NULL;
    result = PyObject_GetAttr(v, oname);
    return result;
}

UPCALL_ID(PyObject_Bytes);
PyObject * PyObject_Bytes(PyObject *v) {
    if (v == NULL) {
        return PyBytes_FromString("<NULL>");
    }
    return UPCALL_CEXT_O(_jls_PyObject_Bytes, native_to_java(v));
}

// taken from CPython 'Objects/object.c'
PyObject * _PyObject_NextNotImplemented(PyObject *self) {
    PyErr_Format(PyExc_TypeError,
                 "'%.200s' object is not iterable",
                 Py_TYPE(self)->tp_name);
    return NULL;
}

#undef _Py_Dealloc

void
_Py_Dealloc(PyObject *op)
{
	op = native_pointer_to_java(op);
    destructor dealloc = Py_TYPE(op)->tp_dealloc;
#ifdef Py_TRACE_REFS
    _Py_ForgetReference(op);
#endif
    (*dealloc)(op);
}

void
_Py_NewReference(PyObject *op)
{
    if (_Py_tracemalloc_config.tracing) {
        _PyTraceMalloc_NewReference(op);
    }
#ifdef Py_REF_DEBUG
    _Py_RefTotal++;
#endif
    Py_SET_REFCNT(op, 1);
#ifdef Py_TRACE_REFS
    _Py_AddToAllObjects(op, 1);
#endif
}
