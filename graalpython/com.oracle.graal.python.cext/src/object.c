/* Copyright (c) 2018, 2024, Oracle and/or its affiliates.
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
    if (points_to_py_handle_space(op) && updated_refcnt <= MANAGED_REFCNT) {
        if (PyTruffle_Debug_CAPI() && updated_refcnt < MANAGED_REFCNT) {
            Py_FatalError("Refcount of native stub fell below MANAGED_REFCNT");
        }
        assert(deferred_notify_cur < DEFERRED_NOTIFY_SIZE);
        deferred_notify_ops[deferred_notify_cur++] = op;
        if (deferred_notify_cur >= DEFERRED_NOTIFY_SIZE) {
            deferred_notify_cur = 0;
            GraalPyTruffle_BulkNotifyRefCount(deferred_notify_ops, DEFERRED_NOTIFY_SIZE);
        }
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
            _decref_notify(op, updated_refcnt);
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
        return (PyVarObject *) PyErr_NoMemory();
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
            fprintf(fp, "<refcnt %ld at %p>",
                (long)Py_REFCNT(op), (void *)op);
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

/* For Python 3.0.1 and later, the old three-way comparison has been
   completely removed in favour of rich comparisons.  PyObject_Compare() and
   PyObject_Cmp() are gone, and the builtin cmp function no longer exists.
   The old tp_compare slot has been renamed to tp_as_async, and should no
   longer be used.  Use tp_richcompare instead.

   See (*) below for practical amendments.

   tp_richcompare gets called with a first argument of the appropriate type
   and a second object of an arbitrary type.  We never do any kind of
   coercion.

   The tp_richcompare slot should return an object, as follows:

    NULL if an exception occurred
    NotImplemented if the requested comparison is not implemented
    any other false value if the requested comparison is false
    any other true value if the requested comparison is true

  The PyObject_RichCompare[Bool]() wrappers raise TypeError when they get
  NotImplemented.

  (*) Practical amendments:

  - If rich comparison returns NotImplemented, == and != are decided by
    comparing the object pointer (i.e. falling back to the base object
    implementation).

*/

/* Map rich comparison operators to their swapped version, e.g. LT <--> GT */
int _Py_SwappedOp[] = {Py_GT, Py_GE, Py_EQ, Py_NE, Py_LT, Py_LE};

static const char * const opstrings[] = {"<", "<=", "==", "!=", ">", ">="};

/* Perform a rich comparison, raising TypeError when the requested comparison
   operator is not supported. */
static PyObject *
do_richcompare(PyObject *v, PyObject *w, int op)
{
    richcmpfunc f;
    PyObject *res;
    int checked_reverse_op = 0;

    if (!Py_IS_TYPE(v, Py_TYPE(w)) &&
        PyType_IsSubtype(Py_TYPE(w), Py_TYPE(v)) &&
        (f = Py_TYPE(w)->tp_richcompare) != NULL) {
        checked_reverse_op = 1;
        res = (*f)(w, v, _Py_SwappedOp[op]);
        if (res != Py_NotImplemented)
            return res;
        Py_DECREF(res);
    }
    if ((f = Py_TYPE(v)->tp_richcompare) != NULL) {
        res = (*f)(v, w, op);
        if (res != Py_NotImplemented)
            return res;
        Py_DECREF(res);
    }
    if (!checked_reverse_op && (f = Py_TYPE(w)->tp_richcompare) != NULL) {
        res = (*f)(w, v, _Py_SwappedOp[op]);
        if (res != Py_NotImplemented)
            return res;
        Py_DECREF(res);
    }
    /* If neither object implements it, provide a sensible default
       for == and !=, but raise an exception for ordering. */
    switch (op) {
    case Py_EQ:
        res = (v == w) ? Py_True : Py_False;
        break;
    case Py_NE:
        res = (v != w) ? Py_True : Py_False;
        break;
    default:
        PyErr_Format(PyExc_TypeError,
                      "'%s' not supported between instances of '%.100s' and '%.100s'",
                      opstrings[op],
                      Py_TYPE(v)->tp_name,
                      Py_TYPE(w)->tp_name);
        return NULL;
    }
    Py_INCREF(res);
    return res;
}

/* Perform a rich comparison with object result.  This wraps do_richcompare()
   with a check for NULL arguments and a recursion check. */

PyObject *
PyObject_RichCompare(PyObject *v, PyObject *w, int op)
{
    assert(Py_LT <= op && op <= Py_GE);
    if (v == NULL || w == NULL) {
        if (!PyErr_Occurred()) {
            PyErr_BadInternalCall();
        }
        return NULL;
    }
    if (Py_EnterRecursiveCall(" in comparison")) {
        return NULL;
    }
    PyObject *res = do_richcompare(v, w, op);
    Py_LeaveRecursiveCall();
    return res;
}

/* Perform a rich comparison with integer result.  This wraps
   PyObject_RichCompare(), returning -1 for error, 0 for false, 1 for true. */
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
    if (PyBool_Check(res))
        ok = (res == Py_True);
    else
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
    /* GraalPy change
    if (_Py_tracemalloc_config.tracing) {
        _PyTraceMalloc_NewReference(op);
    }
    */
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

// GraalPy additions
Py_ssize_t PyTruffle_REFCNT(PyObject *obj) {
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

// alias, currently used in PyO3
Py_ssize_t _Py_REFCNT(PyObject *obj) {
    return PyTruffle_REFCNT(obj);
}

void PyTruffle_SET_REFCNT(PyObject* obj, Py_ssize_t cnt) {
#ifndef GRAALVM_PYTHON_LLVM_MANAGED
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
#endif /* GRAALVM_PYTHON_LLVM_MANAGED */
}

PyTypeObject* PyTruffle_TYPE(PyObject *a) {
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

// alias, currently used in PyO3
PyTypeObject* _Py_TYPE(PyObject *obj) {
    return PyTruffle_TYPE(obj);
}

Py_ssize_t PyTruffle_SIZE(PyObject *ob) {
    PyVarObject* a = (PyVarObject*)ob;
#ifdef GRAALVM_PYTHON_LLVM_MANAGED
	return PyVarObject_ob_size(a);
#else /* GRAALVM_PYTHON_LLVM_MANAGED */
    Py_ssize_t res;
    if (points_to_py_handle_space(a))
    {
        PyObject *ptr = pointer_to_stub((PyObject *) a);
        /*
         * Only do that for tuples right now but we may extend that to any
         * PyVarObject in future.
         */
        if (ptr->ob_type == &PyTuple_Type) {
            res = ((PyVarObject *) ptr)->ob_size;
#ifndef NDEBUG
            if (PyTruffle_Debug_CAPI() && GraalPy_get_PyVarObject_ob_size(a) != res)
            {
                Py_FatalError("ob_size of native stub and managed object differ");
            }
#endif
        }
        else
        {
            res = GraalPy_get_PyVarObject_ob_size(a);
        }
    }
    else
    {
        res = a->ob_size;
    }
	return res;
#endif /* GRAALVM_PYTHON_LLVM_MANAGED */
}

// alias, currently used in PyO3
Py_ssize_t _Py_SIZE(PyObject *obj) {
    return PyTruffle_SIZE(obj);
}

void PyTruffle_SET_TYPE(PyObject *a, PyTypeObject *b) {
	if (points_to_py_handle_space(a)) {
		printf("changing the type of an object is not supported\n");
	} else {
		a->ob_type = b;
	}
}
void PyTruffle_SET_SIZE(PyVarObject *a, Py_ssize_t b) {
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

void PyObject_GC_Track(void* a) {
    if (PyTruffle_Trace_Memory()) {
        GraalPyTruffleObject_GC_Track(a);
    }
}

void PyObject_GC_UnTrack(void* a) {
    if (PyTruffle_Trace_Memory()) {
        GraalPyTruffleObject_GC_UnTrack(a);
    }
}
