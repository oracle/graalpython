/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "pycore_tuple.h"

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

int PyObject_GenericInit(PyObject* self, PyObject* args, PyObject* kwds) {
    return self;
}

#define IS_SINGLE_ARG(_fmt) ((_fmt[0]) != '\0' && (_fmt[1]) == '\0')

PyObject* PyObject_Call(PyObject* callable, PyObject* args, PyObject* kwargs) {
	return Graal_PyTruffleObject_Call1(callable, args, kwargs, 0);
}

PyObject* PyObject_CallObject(PyObject* callable, PyObject* args) {
	return Graal_PyTruffleObject_Call1(callable, args, NULL, 0);
}

PyObject* PyObject_CallFunction(PyObject* callable, const char* fmt, ...) {
    if (fmt == NULL || fmt[0] == '\0') {
	    return Graal_PyTruffleObject_Call1(callable, NULL, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    PyObject* args = Py_VaBuildValue(fmt, va);
    va_end(va);
	return Graal_PyTruffleObject_Call1(callable, args, NULL, IS_SINGLE_ARG(fmt));
}

PyObject* _PyObject_CallFunction_SizeT(PyObject* callable, const char* fmt, ...) {
    if (fmt == NULL || fmt[0] == '\0') {
	    return Graal_PyTruffleObject_Call1(callable, NULL, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    PyObject* args = Py_VaBuildValue(fmt, va);
    va_end(va);
	return Graal_PyTruffleObject_Call1(callable, args, NULL, IS_SINGLE_ARG(fmt));
}

typedef PyObject *(*call_fun_obj_args_t)(PyObject *, void *);
PyObject* PyObject_CallFunctionObjArgs(PyObject *callable, ...) {
    va_list vargs;
    va_start(vargs, callable);
    // the arguments are given as a variable list followed by NULL
    PyObject *result = GraalPyTruffleObject_CallFunctionObjArgs(callable, &vargs);
    va_end(vargs);
    return result;
}

typedef PyObject *(*call_method_t)(PyObject *, void *, void *, int32_t);
PyObject* PyObject_CallMethod(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    if (fmt == NULL || fmt[0] == '\0') {
        return Graal_PyTruffleObject_CallMethod1(object, truffleString(method), NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    args = Py_VaBuildValue(fmt, va);
    va_end(va);
    return Graal_PyTruffleObject_CallMethod1(object, truffleString(method), args, IS_SINGLE_ARG(fmt));
}

typedef PyObject *(*call_meth_obj_args_t)(PyObject *, void *, void *);
PyObject* PyObject_CallMethodObjArgs(PyObject *callable, PyObject *name, ...) {
    va_list vargs;
    va_start(vargs, name);
    // the arguments are given as a variable list followed by NULL
    PyObject *result = GraalPyTruffleObject_CallMethodObjArgs(callable, name, &vargs);
    va_end(vargs);
    return result;
}

PyObject* _PyObject_CallMethod_SizeT(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    if (fmt == NULL || fmt[0] == '\0') {
        return Graal_PyTruffleObject_CallMethod1(object, truffleString(method), NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    args = Py_VaBuildValue(fmt, va);
    va_end(va);
    return Graal_PyTruffleObject_CallMethod1(object, truffleString(method), args, IS_SINGLE_ARG(fmt));
}

PyObject* _PyObject_MakeTpCall(PyThreadState *tstate, PyObject *callable,
                     PyObject *const *args, Py_ssize_t nargs,
                     PyObject *keywords) {
    void* kwvalues = NULL;
    if (keywords != NULL && PyTuple_Check(keywords)) {
        kwvalues = polyglot_from_PyObjectPtr_array(args + nargs, PyTuple_GET_SIZE(keywords));
    }
    // We have to pass nargs separately because of GR-35465 - Sulong ignores the array size for our wrappers
    return Graal_PyTruffleObject_MakeTpCall(callable, polyglot_from_PyObjectPtr_array(args, nargs), nargs, keywords, kwvalues);
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

// Taken from CPython object.c
int
PyObject_GenericSetDict(PyObject *obj, PyObject *value, void *context)
{
    PyObject **dictptr = _PyObject_GetDictPtr(obj);
    if (dictptr == NULL) {
        PyErr_SetString(PyExc_AttributeError,
                        "This object has no __dict__");
        return -1;
    }
    if (value == NULL) {
        PyErr_SetString(PyExc_TypeError, "cannot delete __dict__");
        return -1;
    }
    if (!PyDict_Check(value)) {
        PyErr_Format(PyExc_TypeError,
                     "__dict__ must be set to a dictionary, "
                     "not a '%.200s'", Py_TYPE(value)->tp_name);
        return -1;
    }
    Py_INCREF(value);
    Py_XSETREF(*dictptr, value);
    return 0;
}

// Taken from CPython
int PyObject_Print(PyObject *op, FILE *fp, int flags)
{
    int ret = 0;
    clearerr(fp); /* Clear any previous error condition */
    if (op == NULL) {
        Py_BEGIN_ALLOW_THREADS
        fprintf(fp, "<nil>");
        Py_END_ALLOW_THREADS
    }
    else {
        if (Py_REFCNT(op) <= 0) {
            /* XXX(twouters) cast refcount to long until %zd is
               universally available */
            Py_BEGIN_ALLOW_THREADS
            fprintf(fp, "<refcnt %ld at %p>", (long) Py_REFCNT(op), (void *)op);
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

//    printf("looking for %s\n", name);
    if (Py_TYPE(v)->tp_getattr != NULL) {
//    	printf("calling tp_getattr\n");
        return (*Py_TYPE(v)->tp_getattr)(v, (char*)name);
    }
//	printf("calling PyUnicode_FromString\n");
    PyObject *w = PyUnicode_FromString(name);
//	printf("calling NULL?\n");
    if (w == NULL) {
        return NULL;
    }
//	printf("calling PyObject_GetAttr\n");
    PyObject *res = PyObject_GetAttr(v, w);
    Py_DecRef(w);
    return res;
}


// taken from CPython "Objects/object.c"
int PyObject_SetAttrString(PyObject *v, const char *name, PyObject *w) {
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

/* Note: We must implement this in native because it might happen that this function is used on an
   uninitialized type which means that a managed attribute lookup won't work. */
// taken from CPython "Objects/object.c"
PyObject * PyObject_GetAttr(PyObject *v, PyObject *name) {
    PyTypeObject *tp = Py_TYPE(v);
//	printf("PyObject_GetAttr: calling PyUnicode_Check\n");

    if (!PyUnicode_Check(name)) {
//    	printf("PyObject_GetAttr: calling PyExc_TypeError\n");
        PyErr_Format(PyExc_TypeError,
                     "attribute name must be string, not '%.200s'",
					 Py_TYPE(name)->tp_name);
        return NULL;
    }
    if (tp->tp_getattro != NULL) {
//    	printf("PyObject_GetAttr: calling tp_getattro\n");
//        GraalPyTruffle_Debug(v, name);
        return (*tp->tp_getattro)(v, name);
    }
    if (tp->tp_getattr != NULL) {
//    	printf("PyObject_GetAttr: calling PyUnicode_AsUTF8\n");
        const char *name_str = PyUnicode_AsUTF8(name);
        if (name_str == NULL) {
            return NULL;
        }
        return (*tp->tp_getattr)(v, (char *)name_str);
    }
//	printf("PyObject_GetAttr: calling PyExc_AttributeError\n");
    PyErr_Format(PyExc_AttributeError,
                 "'%.50s' object has no attribute '%U'",
                 tp->tp_name, name);
    return NULL;
}

// taken from CPython "Objects/object.c"
int _PyObject_LookupAttr(PyObject *v, PyObject *name, PyObject **result) {
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
int _PyObject_LookupAttrId(PyObject *v, _Py_Identifier *name, PyObject **result) {
    PyObject *oname = _PyUnicode_FromId(name); /* borrowed */
    if (!oname) {
        *result = NULL;
        return -1;
    }
    return  _PyObject_LookupAttr(v, oname, result);
}

PyObject* PyObject_GenericGetAttr(PyObject* obj, PyObject* attr) {
    PyTypeObject *tp = Py_TYPE(obj);
    if (tp->tp_dict == NULL && PyType_Ready(tp) < 0) {
    	return NULL;
    }
	return GraalPyTruffleObject_GenericGetAttr(obj, attr);
}

/* Note: We must implement this in native because it might happen that this function is used on an
   unitialized type which means that a managed attribute lookup won't work. */
// taken from CPython "Objects/object.c"
int PyObject_SetAttr(PyObject *v, PyObject *name, PyObject *value) {
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
int _PyObject_SetAttrId(PyObject *v, _Py_Identifier *name, PyObject *w) {
    int result;
    PyObject *oname = _PyUnicode_FromId(name); /* borrowed */
    if (!oname)
        return -1;
    result = PyObject_SetAttr(v, oname, w);
    return result;
}

int PyObject_GenericSetAttr(PyObject* obj, PyObject* attr, PyObject* value) {
    PyTypeObject *tp = Py_TYPE(obj);
    if (tp->tp_dict == NULL && PyType_Ready(tp) < 0) {
        return -1;
    }
	return (int) GraalPyTruffleObject_GenericSetAttr(obj, attr, value);
}

int PyObject_Not(PyObject* obj) {
    return PyObject_IsTrue(obj) ? 0 : 1;
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

void PyObject_GC_Del(void *tp) {
	PyObject_Free(tp);
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

void _Py_IncRef(PyObject *op) {
    Py_SET_REFCNT(op, Py_REFCNT(op) + 1);
}

void _Py_DecRef(PyObject *op) {
    Py_ssize_t cnt = Py_REFCNT(op) - 1;
    Py_SET_REFCNT(op, cnt);
    if (cnt != 0) {
    }
    else {
        _Py_Dealloc(op);
    }
}
void Py_IncRef(PyObject *op) {
	if (op != NULL) {
		_Py_IncRef(op);
	}
}

void Py_DecRef(PyObject *op) {
	if (op != NULL) {
		_Py_DecRef(op);
	}
}

PyObject* PyObject_Init(PyObject *op, PyTypeObject *tp) {
    if (op == NULL) {
        return PyErr_NoMemory();
    }

    _PyObject_Init(op, tp);
    return op;
}

// taken from CPython "Objects/object.c"
PyVarObject * PyObject_InitVar(PyVarObject *op, PyTypeObject *tp, Py_ssize_t size) {
    if (op == NULL) {
        return (PyVarObject *) PyErr_NoMemory();
    }

    _PyObject_InitVar(op, tp, size);
    return op;
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

#undef Py_NewRef
#undef Py_XNewRef

// Export Py_NewRef() and Py_XNewRef() as regular functions for the stable ABI.
PyObject*
Py_NewRef(PyObject *obj)
{
    return _Py_NewRef(obj);
}

PyObject*
Py_XNewRef(PyObject *obj)
{
    return _Py_XNewRef(obj);
}
