/* Copyright (c) 2018, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "capi.h"

#include "pycore_pymem.h"
#include "pycore_object.h"

PyObject* PyObject_SelfIter(PyObject* obj) {
    Py_INCREF(obj);
    return obj;
}

#undef Py_REFCNT
Py_ssize_t Py_REFCNT(PyObject *obj) {
	return PyObject_ob_refcnt(obj);
}

#undef Py_SET_REFCNT
void Py_SET_REFCNT(PyObject* obj, Py_ssize_t cnt) {
	set_PyObject_ob_refcnt(obj, cnt);
}

#undef Py_TYPE
PyTypeObject* Py_TYPE(PyObject *a) {
	return PyObject_ob_type(a);
}

#undef Py_SIZE
Py_ssize_t Py_SIZE(PyObject *a) {
	return PyVarObject_ob_size(a);
}

#undef Py_SET_TYPE
void Py_SET_TYPE(PyObject *a, PyTypeObject *b) {
	if (points_to_py_handle_space(a)) {
		printf("changing the type of an object is not supported\n");
	} else {
		a->ob_type = b;
	}
}

#undef Py_SET_SIZE
void Py_SET_SIZE(PyVarObject *a, Py_ssize_t b) {
	if (points_to_py_handle_space(a)) {
		printf("changing the size of an object is not supported\n");
	} else {
		a->ob_size = b;
	}
}

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
#ifdef Py_REF_DEBUG
    _Py_RefTotal++;
#endif
    Py_SET_REFCNT(op, 1);
#ifdef Py_TRACE_REFS
    _Py_AddToAllObjects(op, 1);
#endif
}

PyObject* PyObject_Init(PyObject *op, PyTypeObject *tp) {
    if (op == NULL) {
        return PyErr_NoMemory();
    }

    _PyObject_Init(op, tp);
    return op;
}

// taken from CPython "Objects/object.c"
PyVarObject* PyObject_InitVar(PyVarObject *op, PyTypeObject *tp, Py_ssize_t size) {
    if (op == NULL) {
        return (PyVarObject*) PyErr_NoMemory();
    }

    _PyObject_InitVar(op, tp, size);
    return op;
}

PyObject* _PyObject_New(PyTypeObject *tp) {
    PyObject *op = (PyObject*)PyObject_MALLOC(_PyObject_SIZE(tp));
    if (op == NULL) {
        return PyErr_NoMemory();
    }
    return PyObject_INIT(op, tp);
}

PyVarObject* _PyObject_NewVar(PyTypeObject *tp, Py_ssize_t nitems) {
    PyVarObject* op;
    const size_t size = _PyObject_VAR_SIZE(tp, nitems);
    op = (PyVarObject*) PyObject_MALLOC(size);
    if (op == NULL)
        return (PyVarObject*)PyErr_NoMemory();
    return PyObject_INIT_VAR(op, tp, nitems);
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

// Taken from cpython object.c
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

    if (Py_TYPE(v)->tp_getattr != NULL) {
        return (*Py_TYPE(v)->tp_getattr)(v, (char*)name);
    }
    PyObject *w = PyUnicode_FromString(name);
    if (w == NULL) {
        return NULL;
    }
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

    if (!PyUnicode_Check(name)) {
        PyErr_Format(PyExc_TypeError,
                     "attribute name must be string, not '%.200s'",
					 Py_TYPE(name)->tp_name);
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
