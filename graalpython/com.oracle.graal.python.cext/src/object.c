/* Copyright (c) 2018, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "capi.h"


/* Generic object operations; and implementation of None */

#include "pycore_object.h"
#include "pycore_pymem.h"         // _PyMem_IsPtrFreed()

// 134
void Py_IncRef(PyObject *op)
{
    if (op != NULL) {
        _Py_IncRef(op);
    }
}

// 141
void Py_DecRef(PyObject *op)
{
    if (op != NULL) {
        _Py_DecRef(op);
    }
}

// 146
void _Py_IncRef(PyObject *op) {
    const Py_ssize_t refcnt = Py_REFCNT(op);
    if (refcnt != IMMORTAL_REFCNT)
    {
        Py_SET_REFCNT(op, refcnt + 1);
        if (points_to_py_handle_space(op) && refcnt == MANAGED_REFCNT) {
            GraalPyTruffle_NotifyRefCount(op, refcnt + 1);
        }
    }
}

#define DEFERRED_NOTIFY_SIZE 16
static PyObject *deferred_notify_ops[DEFERRED_NOTIFY_SIZE];
static int deferred_notify_cur = 0;

static inline void
_decref_notify(const PyObject *op, const Py_ssize_t updated_refcnt)
{
    if (points_to_py_handle_space(op) && updated_refcnt == MANAGED_REFCNT) {
        if (deferred_notify_cur >= DEFERRED_NOTIFY_SIZE) {
            deferred_notify_cur = 0;
            GraalPyTruffle_BulkNotifyRefCount(deferred_notify_ops, DEFERRED_NOTIFY_SIZE);
        }
        assert(deferred_notify_cur < DEFERRED_NOTIFY_SIZE);
        deferred_notify_ops[deferred_notify_cur++] = op;
    }
}

// 152
void _Py_DecRef(PyObject *op) {
    const Py_ssize_t refcnt = Py_REFCNT(op);
    if (refcnt != IMMORTAL_REFCNT)
    {
        const Py_ssize_t updated_refcnt = refcnt - 1;
        Py_SET_REFCNT(op, updated_refcnt);
        if (updated_refcnt != 0) {
            _decref_notify(op, refcnt);
        }
        else {
            _Py_Dealloc(op);
        }
    }
}

// 158
PyObject *
PyObject_Init(PyObject *op, PyTypeObject *tp)
{
    if (op == NULL) {
        return PyErr_NoMemory();
    }

    _PyObject_Init(op, tp);
    return op;
}

// 169
PyVarObject *
PyObject_InitVar(PyVarObject *op, PyTypeObject *tp, Py_ssize_t size)
{
    if (op == NULL) {
        return (PyVarObject*) PyErr_NoMemory();
    }

    _PyObject_InitVar(op, tp, size);
    return op;
}

// 180
PyObject *
_PyObject_New(PyTypeObject *tp)
{
    PyObject *op = (PyObject *) PyObject_Malloc(_PyObject_SIZE(tp));
    if (op == NULL) {
        return PyErr_NoMemory();
    }
    _PyObject_Init(op, tp);
    return op;
}

// 191
PyVarObject *
_PyObject_NewVar(PyTypeObject *tp, Py_ssize_t nitems)
{
    PyVarObject *op;
    const size_t size = _PyObject_VAR_SIZE(tp, nitems);
    op = (PyVarObject *) PyObject_Malloc(size);
    if (op == NULL) {
        return (PyVarObject *)PyErr_NoMemory();
    }
    _PyObject_InitVar(op, tp, nitems);
    return op;
}

// 263
int
PyObject_Print(PyObject *op, FILE *fp, int flags)
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

// 751
int
PyObject_RichCompareBool(PyObject *v, PyObject *w, int op)
{
    PyObject *res;
    int ok;

    /* Quick result when objects are the same.
       Guarantees that identity implies equality. */
    if (v == w) {
        if (op == Py_EQ)
            return 1;
        else if (op == Py_NE)
            return 0;
    }

    res = PyObject_RichCompare(v, w, op);
    if (res == NULL)
        return -1;
    ok = PyObject_IsTrue(res);
    Py_DECREF(res);
    return ok;
}

// 805
PyObject *
PyObject_GetAttrString(PyObject *v, const char *name)
{
    PyObject *w, *res;

    if (Py_TYPE(v)->tp_getattr != NULL)
        return (*Py_TYPE(v)->tp_getattr)(v, (char*)name);
    w = PyUnicode_FromString(name);
    if (w == NULL)
        return NULL;
    res = PyObject_GetAttr(v, w);
    Py_DECREF(w);
    return res;
}


// 832
int
PyObject_SetAttrString(PyObject *v, const char *name, PyObject *w)
{
    PyObject *s;
    int res;

    if (Py_TYPE(v)->tp_setattr != NULL)
        return (*Py_TYPE(v)->tp_setattr)(v, (char*)name, w);
    // TODO(fa): CPython interns strings; verify if that makes sense for us as well
    // s = PyUnicode_InternFromString(name);
    s = PyUnicode_FromString(name);
    if (s == NULL)
        return -1;
    res = PyObject_SetAttr(v, s, w);
    Py_XDECREF(s);
    return res;
}

// 865
PyObject *
_PyObject_GetAttrId(PyObject *v, _Py_Identifier *name)
{
    PyObject *result;
    PyObject *oname = _PyUnicode_FromId(name); /* borrowed */
    if (!oname)
        return NULL;
    result = PyObject_GetAttr(v, oname);
    return result;
}

// 876
int
_PyObject_SetAttrId(PyObject *v, _Py_Identifier *name, PyObject *w)
{
    int result;
    PyObject *oname = _PyUnicode_FromId(name); /* borrowed */
    if (!oname)
        return -1;
    result = PyObject_SetAttr(v, oname, w);
    return result;
}

/* Note: We must implement this in native because it might happen that this function is used on an
   uninitialized type which means that a managed attribute lookup won't work. */
// 919
PyObject *
PyObject_GetAttr(PyObject *v, PyObject *name)
{
    PyTypeObject *tp = Py_TYPE(v);
    if (!PyUnicode_Check(name)) {
        PyErr_Format(PyExc_TypeError,
                     "attribute name must be string, not '%.200s'",
                     Py_TYPE(name)->tp_name);
        return NULL;
    }

    PyObject* result = NULL;
    if (tp->tp_getattro != NULL) {
        result = (*tp->tp_getattro)(v, name);
    }
    else if (tp->tp_getattr != NULL) {
        const char *name_str = PyUnicode_AsUTF8(name);
        if (name_str == NULL) {
            return NULL;
        }
        result = (*tp->tp_getattr)(v, (char *)name_str);
    }
    else {
        PyErr_Format(PyExc_AttributeError,
                    "'%.50s' object has no attribute '%U'",
                    tp->tp_name, name);
    }

    if (result == NULL) {
        // set_attribute_error_context(v, name);
    }
    return result;
}

// 953
int
_PyObject_LookupAttr(PyObject *v, PyObject *name, PyObject **result)
{
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

// 1002
int
_PyObject_LookupAttrId(PyObject *v, _Py_Identifier *name, PyObject **result)
{
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
// 1029
int
PyObject_SetAttr(PyObject *v, PyObject *name, PyObject *value)
{
    PyTypeObject *tp = Py_TYPE(v);
    int err;

    if (!PyUnicode_Check(name)) {
        PyErr_Format(PyExc_TypeError,
                     "attribute name must be string, not '%.200s'",
                     Py_TYPE(name)->tp_name);
        return -1;
    }
    Py_INCREF(name);

    // TODO(fa): CPython interns strings; verify if that makes sense for us as well
    // PyUnicode_InternInPlace(&name);
    if (tp->tp_setattro != NULL) {
        err = (*tp->tp_setattro)(v, name, value);
        Py_DECREF(name);
        return err;
    }
    if (tp->tp_setattr != NULL) {
        const char *name_str = PyUnicode_AsUTF8(name);
        if (name_str == NULL) {
            Py_DECREF(name);
            return -1;
        }
        err = (*tp->tp_setattr)(v, (char *)name_str, value);
        Py_DECREF(name);
        return err;
    }
    Py_DECREF(name);
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

// 1103
PyObject *
PyObject_SelfIter(PyObject *obj)
{
    Py_INCREF(obj);
    return obj;
}

// 1115
PyObject *
_PyObject_NextNotImplemented(PyObject *self)
{
    PyErr_Format(PyExc_TypeError,
                 "'%.200s' object is not iterable",
                 Py_TYPE(self)->tp_name);
    return NULL;
}

// 1125
/* Specialized version of _PyObject_GenericGetAttrWithDict
   specifically for the LOAD_METHOD opcode.

   Return 1 if a method is found, 0 if it's a regular attribute
   from __dict__ or something returned by using a descriptor
   protocol.

   `method` will point to the resolved attribute or NULL.  In the
   latter case, an error will be set.
*/
int
_PyObject_GetMethod(PyObject *obj, PyObject *name, PyObject **method)
{
    PyTypeObject *tp = Py_TYPE(obj);
    PyObject *descr;
    descrgetfunc f = NULL;
    PyObject **dictptr, *dict;
    PyObject *attr;
    int meth_found = 0;

    assert(*method == NULL);

    if (Py_TYPE(obj)->tp_getattro != PyObject_GenericGetAttr
            || !PyUnicode_Check(name)) {
        *method = PyObject_GetAttr(obj, name);
        return 0;
    }

    if (tp->tp_dict == NULL && PyType_Ready(tp) < 0)
        return 0;

    descr = _PyType_Lookup(tp, name);
    if (descr != NULL) {
        Py_INCREF(descr);
        if (_PyType_HasFeature(Py_TYPE(descr), Py_TPFLAGS_METHOD_DESCRIPTOR)) {
            meth_found = 1;
        } else {
            f = Py_TYPE(descr)->tp_descr_get;
            if (f != NULL && PyDescr_IsData(descr)) {
                *method = f(descr, obj, (PyObject *)Py_TYPE(obj));
                Py_DECREF(descr);
                return 0;
            }
        }
    }

    dictptr = _PyObject_GetDictPtr(obj);
    if (dictptr != NULL && (dict = *dictptr) != NULL) {
        Py_INCREF(dict);
        attr = PyDict_GetItemWithError(dict, name);
        if (attr != NULL) {
            Py_INCREF(attr);
            *method = attr;
            Py_DECREF(dict);
            Py_XDECREF(descr);
            return 0;
        }
        else {
            Py_DECREF(dict);
            if (PyErr_Occurred()) {
                Py_XDECREF(descr);
                return 0;
            }
        }
    }

    if (meth_found) {
        *method = descr;
        return 1;
    }

    if (f != NULL) {
        *method = f(descr, obj, (PyObject *)Py_TYPE(obj));
        Py_DECREF(descr);
        return 0;
    }

    if (descr != NULL) {
        *method = descr;
        return 0;
    }

    PyErr_Format(PyExc_AttributeError,
                 "'%.50s' object has no attribute '%U'",
                 tp->tp_name, name);

//    set_attribute_error_context(obj, name);
    return 0;
}

// 1903
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

// 1413
int
PyObject_GenericSetAttr(PyObject *obj, PyObject *name, PyObject *value)
{
    PyTypeObject *tp = Py_TYPE(obj);
    if (tp->tp_dict == NULL && PyType_Ready(tp) < 0) {
        return -1;
    }
    return (int) GraalPyTruffleObject_GenericSetAttr(obj, name, value);
}

// 1419
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

// 1475
int
PyObject_Not(PyObject *v)
{
    int res;
    res = PyObject_IsTrue(v);
    if (res < 0)
        return res;
    return res == 0;
}

#undef _Py_Dealloc
// 2294
void
_Py_Dealloc(PyObject *op)
{
    destructor dealloc = Py_TYPE(op)->tp_dealloc;
#ifdef Py_TRACE_REFS
    _Py_ForgetReference(op);
#endif
    (*dealloc)(op);
}

// 2312
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

// 2328
#undef Py_Is
#undef Py_IsNone
#undef Py_IsTrue
#undef Py_IsFalse

// Export Py_Is(), Py_IsNone(), Py_IsTrue(), Py_IsFalse() as regular functions
// for the stable ABI.
int Py_Is(PyObject *x, PyObject *y)
{
    return (x == y);
}

int Py_IsNone(PyObject *x)
{
    return Py_Is(x, Py_None);
}

int Py_IsTrue(PyObject *x)
{
    return Py_Is(x, Py_True);
}

int Py_IsFalse(PyObject *x)
{
    return Py_Is(x, Py_False);
}

// Implementations for cpython call.c
static PyObject *const *
_PyStack_UnpackDict(PyObject *const *args, Py_ssize_t nargs,
                    PyObject *kwargs, PyObject **p_kwnames);
static void
_PyStack_UnpackDict_Free(PyObject *const *stack, Py_ssize_t nargs,
                         PyObject *kwnames);

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

PyObject *
PyObject_VectorcallMethod(PyObject *name, PyObject *const *args,
                           size_t nargsf, PyObject *kwnames)
{
    assert(name != NULL);
    assert(args != NULL);
    assert(PyVectorcall_NARGS(nargsf) >= 1);

    PyThreadState *tstate = _PyThreadState_GET();
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
    PyObject *result = _PyObject_VectorcallTstate(tstate, callable,
                                                  args, nargsf, kwnames);
    Py_DECREF(callable);
    return result;
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

// GraalPy additions
Py_ssize_t _Py_REFCNT(const PyObject *obj) {
#ifdef GRAALVM_PYTHON_LLVM_MANAGED
    return IMMORTAL_REFCNT;
#else /* GRAALVM_PYTHON_LLVM_MANAGED */
    Py_ssize_t res;
    if (points_to_py_handle_space(obj))
    {
        res = pointer_to_stub(obj)->ob_refcnt;
#ifndef NDEBUG
        if (PyTruffle_Debug_CAPI() && PyObject_ob_refcnt(obj) != res)
        {
            Py_FatalError("Refcount of native stub and managed object differ");
        }
#endif
    }
    else
    {
        res = obj->ob_refcnt;
    }
    return res;
#endif /* GRAALVM_PYTHON_LLVM_MANAGED */
}

Py_ssize_t _Py_SET_REFCNT(PyObject* obj, Py_ssize_t cnt) {
#ifdef GRAALVM_PYTHON_LLVM_MANAGED
    return IMMORTAL_REFCNT;
#else /* GRAALVM_PYTHON_LLVM_MANAGED */
    PyObject *dest;
    if (points_to_py_handle_space(obj))
    {
        dest = pointer_to_stub(obj);
#ifndef NDEBUG
        if (PyTruffle_Debug_CAPI())
        {
            set_PyObject_ob_refcnt(obj, cnt);
        }
#endif
    }
    else
    {
        dest = obj;
    }
    dest->ob_refcnt = cnt;
	return cnt;
#endif /* GRAALVM_PYTHON_LLVM_MANAGED */
}

PyTypeObject* _Py_TYPE(const PyObject *a) {
#ifdef GRAALVM_PYTHON_LLVM_MANAGED
    return PyObject_ob_type(a);
#else /* GRAALVM_PYTHON_LLVM_MANAGED */
    PyTypeObject *res;
    if (points_to_py_handle_space(a))
    {
        res = pointer_to_stub(a)->ob_type;
#ifndef NDEBUG
        if (PyTruffle_Debug_CAPI() && PyObject_ob_type(a) != res)
        {
            Py_FatalError("Type of native stub and managed object differ");
        }
#endif
    }
    else
    {
        res = a->ob_type;
    }
    return res;
#endif /* GRAALVM_PYTHON_LLVM_MANAGED */
}

Py_ssize_t _Py_SIZE(const PyVarObject *a) {
	return PyVarObject_ob_size(a);
}

void _Py_SET_TYPE(PyObject *a, PyTypeObject *b) {
	if (points_to_py_handle_space(a)) {
		printf("changing the type of an object is not supported\n");
	} else {
		a->ob_type = b;
	}
}
void _Py_SET_SIZE(PyVarObject *a, Py_ssize_t b) {
	if (points_to_py_handle_space(a)) {
		printf("changing the size of an object is not supported\n");
	} else {
		a->ob_size = b;
	}
}

PyObject* _PyObject_GC_New(PyTypeObject *tp) {
    return _PyObject_New(tp);
}

PyVarObject* _PyObject_GC_NewVar(PyTypeObject *tp, Py_ssize_t nitems) {
    return _PyObject_NewVar(tp, nitems);
}

void PyObject_GC_Del(void *tp) {
	PyObject_Free(tp);
}
