/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
    PyObject* newObj = (PyObject*)PyObject_Malloc(cls->tp_basicsize + cls->tp_itemsize * nitems);
    newObj->ob_refcnt = 0;
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

void PyObject_Free(void* ptr) {
    free(ptr);
}

Py_ssize_t PyObject_Size(PyObject *o) {
    return UPCALL_CEXT_L("PyObject_Size", native_to_java(o));
}

static int add_subclass(PyTypeObject *base, PyTypeObject *type) {
    void* key = PyLong_FromVoidPtr((void *) type);
    if (key == NULL) {
        return -1;
    }
    if (polyglot_is_value(base)) {
        return polyglot_as_i32(polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Add_Subclass", native_to_java((PyObject*)base), native_to_java(key), native_to_java((PyObject*)type)));
    } else {
        PyObject *dict = base->tp_subclasses;
        if (dict == NULL) {
            base->tp_subclasses = dict = PyDict_New();
            if (dict == NULL) {
                return -1;
            }
        }
        // TODO value should be a weak reference !
        return PyDict_SetItem(base->tp_subclasses, key, (PyObject*)type);
    }
	return -1;
}

/* Special C landing functions that convert some arguments to primitives. */

static PyObject* wrap_allocfunc(allocfunc f, PyTypeObject* klass, PyObject* n) {
	return f(klass, PyLong_AsSsize_t(n));
}

/* Wrapper around a native function to be called by Python code. */
static PyObject* wrap_getattrfunc(getattrfunc f, PyObject* obj, PyObject* unicode) {
	// we really need to provide 'char *' since this often runs non-Sulong code
	return f(obj, as_char_pointer(unicode));
}

/* Wrapper around the native function to be called by Python code. */
static PyObject* wrap_setattrfunc(setattrfunc f, PyObject* obj, PyObject* unicode, PyObject* value) {
	// we really need to provide 'char *' since this often runs non-Sulong code
	return f(obj, as_char_pointer(unicode), value);
}

static PyObject* wrap_setattrofunc(setattrofunc f, PyObject* obj, PyObject* key, PyObject* item) {
	return PyLong_FromLong(f(obj, key, item));
}

static PyObject* wrap_richcmpfunc(richcmpfunc f, PyObject* a, PyObject* b, PyObject* n) {
	return f(a, b, (int)PyLong_AsLong(n));
}

static PyObject* wrap_ssizeobjargproc(ssizeobjargproc f, PyObject* a, PyObject* size, PyObject* b) {
	return PyLong_FromLong(f(a, PyLong_AsSsize_t(size), b));
}

static PyObject* wrap_ssizeargfunc(ssizeargfunc f, PyObject* a, PyObject* size) {
	return PyLong_FromLong(f(a, PyLong_AsSsize_t(size)));
}

static PyObject* wrap_initproc(initproc f, PyObject* a, PyObject* b, PyObject* c) {
	return PyLong_FromLong(f(a, b,  c));
}

static PyObject* wrap_objobjargproc(objobjargproc f, PyObject* a, PyObject* b, PyObject* c) {
	return PyLong_FromLong(f(a, b,  c));
}

static PyObject* wrap_objobjproc(objobjproc f, PyObject* a, PyObject* b) {
	return PyLong_FromLong(f(a, b));
}

static PyObject* wrap_inquiry(inquiry f, PyObject* a) {
	return PyLong_FromLong(f(a));
}

/* very special case: operator '**' has an optional third arg */
static PyObject* wrap_pow(ternaryfunc f, ...) {
    int nargs = polyglot_get_arg_count();
    switch(nargs) {
    case 3:
        // TODO use 'native_to_java' on result
        return f(polyglot_get_arg(1), polyglot_get_arg(2), Py_None);
    case 4:
        // TODO use 'native_to_java' on result
        return f(polyglot_get_arg(1), polyglot_get_arg(2), polyglot_get_arg(3));
    }
    return Py_NoValue;
}

static PyObject* wrap_lenfunc(lenfunc f, PyObject* a) {
    return PyLong_FromSsize_t(f(a));
}

static Py_hash_t wrap_hashfunc(hashfunc f, PyObject* a) {
    return PyLong_FromSsize_t(f(a));
}


int PyType_Ready(PyTypeObject* cls) {
#define ADD_IF_MISSING(attr, def) if (!(attr)) { attr = def; }
#define ADD_METHOD(m) ADD_METHOD_OR_SLOT(m.ml_name, get_method_flags_cwrapper(m.ml_flags), m.ml_meth, m.ml_flags, m.ml_doc)
#define ADD_SLOT(name, meth, flags) ADD_METHOD_OR_SLOT(name, get_method_flags_cwrapper(flags), meth, flags, name)
#define ADD_SLOT_CONV(name, clanding, meth, flags) ADD_METHOD_OR_SLOT(name, clanding, meth, flags, name)
#define ADD_METHOD_OR_SLOT(name, clanding, meth, flags, doc)                                \
    if (meth) {                                                                             \
        polyglot_invoke(PY_TRUFFLE_CEXT,                                                    \
                       "AddFunction",                                                       \
                       javacls,                                                             \
                       polyglot_from_string((name), SRC_CS),                                \
                       (meth),                                                              \
                       (clanding),                                                          \
                       get_method_flags_wrapper(flags),                                     \
                       polyglot_from_string(doc, SRC_CS),                                   \
                       (flags) > 0 && ((flags) & METH_CLASS) != 0,                          \
                       (flags) > 0 && ((flags) & METH_STATIC) != 0);                        \
    }

    // https://docs.python.org/3/c-api/typeobj.html#Py_TPFLAGS_READY
    if ((cls->tp_flags & Py_TPFLAGS_READY) || (cls->tp_flags & Py_TPFLAGS_READYING)) {
        return 0;
    }
    cls->tp_flags = cls->tp_flags | Py_TPFLAGS_READYING;

    // https://docs.python.org/3/c-api/typeobj.html#c.PyObject.ob_type
    PyTypeObject* base = cls->tp_base;
    PyTypeObject* metaclass = Py_TYPE(cls);
    if (!base) {
        base = &PyBaseObject_Type;
        if (!metaclass) {
            metaclass = &PyType_Type;
        }
    } else {
        if (!metaclass) {
            metaclass = Py_TYPE(base);
        }
    }
    cls->tp_base = base;
    Py_TYPE(cls) = metaclass;

    if (!(cls->tp_doc)) {
        cls->tp_doc = "";
    }

    /* Initialize tp_bases */
    PyObject* bases = cls->tp_bases;
    if (bases == NULL) {
        if (base == NULL) {
            bases = PyTuple_New(0);
        } else {
            bases = PyTuple_Pack(1, base);
        }
        if (bases == NULL) {
        	cls->tp_flags &= ~Py_TPFLAGS_READYING;
        	return -1;
        }
        cls->tp_bases = bases;
    }

    PyObject* native_members = PyDict_New();
    PyDict_SetItemString(native_members, "tp_name", polyglot_from_string(cls->tp_name, SRC_CS));
    PyDict_SetItemString(native_members, "tp_doc", polyglot_from_string(cls->tp_doc ? cls->tp_doc : "", SRC_CS));
    PyDict_SetItemString(native_members, "tp_basicsize", PyLong_FromSsize_t(cls->tp_basicsize));
    const char* lastDot = strrchr(cls->tp_name, '.');
    if (lastDot) {
        PyDict_SetItemString(native_members, "__module__", polyglot_from_string(lastDot + 1, SRC_CS));
    }
    PyTypeObject* javacls = polyglot_invoke(PY_TRUFFLE_CEXT,
                                            "PyType_Ready",
                                            // no conversion of cls here, because we
                                            // store this into the PyTypeObject
                                            cls,
                                            native_to_java((PyObject*)metaclass),
                                            native_to_java(bases),
                                            native_to_java(native_members));

    // remember the managed wrapper
    ((PyObject*)cls)->ob_refcnt = truffle_handle_for_managed(javacls);
    if (cls->tp_dict != NULL) {
        // TODO: (tfel) is this always safe?
        PyDict_Update(javacls->tp_dict, cls->tp_dict);
    }
    cls->tp_dict = javacls->tp_dict;

    PyMethodDef* methods = cls->tp_methods;
    if (methods) {
        int idx = 0;
        PyMethodDef def = methods[idx];
        while (def.ml_name != NULL) {
            if (!(def.ml_doc)) {
                def.ml_doc = "";
            }
            ADD_METHOD(def);
            def = methods[++idx];
        }
    }

    PyMemberDef* members = cls->tp_members;
    if (members) {
        int i = 0;
        PyMemberDef member = members[i];
        while (member.name != NULL) {
            polyglot_invoke(PY_TRUFFLE_CEXT,
                           "AddMember",
                           // TODO(fa): there should actually be 'native_to_java' just in case 'javacls' goes to native in between
                           javacls,
                           polyglot_from_string(member.name, SRC_CS),
                           member.type,
                           member.offset,
                           // TODO: support other flags
                           native_to_java(((member.flags & READONLY) == 0) ? Py_True : Py_False),
                           polyglot_from_string(member.doc ? member.doc : "", SRC_CS));
            member = members[++i];
        }
    }

    PyGetSetDef* getsets = cls->tp_getset;
    if (getsets) {
        int i = 0;
        PyGetSetDef getset = getsets[i];
        while (getset.name != NULL) {
            getter getter_fun = getset.get;
            setter setter_fun = getset.set;
            polyglot_invoke(PY_TRUFFLE_CEXT,
                            "AddGetSet",
                            // TODO(fa): there should actually be 'native_to_java' just in case 'javacls' goes to native in between
                            javacls,
                            polyglot_from_string(getset.name, SRC_CS),
                            getter_fun != NULL ? (getter)getter_fun : native_to_java(Py_None),
                            wrap_direct,
                            setter_fun != NULL ? (setter)setter_fun : native_to_java(Py_None),
                            wrap_setter,
                            getset.doc ? polyglot_from_string(getset.doc, SRC_CS) : polyglot_from_string("", SRC_CS),
                            // do not convert the closure, it is handed to the
                            // getter and setter as-is
                            getset.closure);
            getset = getsets[++i];
        }
    }

    ADD_IF_MISSING(cls->tp_alloc, PyType_GenericAlloc);
    ADD_IF_MISSING(cls->tp_new, PyType_GenericNew);

    // add special methods defined directly on the type structs
    ADD_SLOT("__dealloc__", cls->tp_dealloc, -1);
    ADD_SLOT("__print__", cls->tp_print, -3);
    // https://docs.python.org/3/c-api/typeobj.html#c.PyTypeObject.tp_getattr
    // tp_getattr and tp_setattr are deprecated, and should be the same as
    // tp_getattro and tp_setattro

    // NOTE: The slots may be called from managed code, i.e., we need to wrap the functions
    // and convert arguments that should be C primitives.
    ADD_SLOT_CONV("__getattr__", wrap_getattrfunc, cls->tp_getattr, -2);
    ADD_SLOT_CONV("__setattr__", wrap_setattrfunc, cls->tp_setattr, -3);
    ADD_SLOT("__repr__", cls->tp_repr, -1);
    ADD_SLOT_CONV("__hash__", wrap_hashfunc, cls->tp_hash, -1);
    ADD_SLOT("__call__", cls->tp_call, METH_KEYWORDS | METH_VARARGS);
    ADD_SLOT("__str__", cls->tp_str, -1);
    ADD_SLOT("__getattr__", cls->tp_getattro, -2);
    ADD_SLOT_CONV("__setattr__", wrap_setattrofunc, cls->tp_setattro, -3);
    ADD_SLOT("__clear__", cls->tp_clear, -1);
    ADD_SLOT_CONV("__compare__", wrap_richcmpfunc, cls->tp_richcompare, -3);
    ADD_SLOT("__iter__", cls->tp_iter, -1);
    ADD_SLOT("__next__", cls->tp_iternext, -1);
    ADD_SLOT("__get__", cls->tp_descr_get, -3);
    ADD_SLOT("__set__", cls->tp_descr_set, -3);
    ADD_SLOT_CONV("__init__", wrap_initproc, cls->tp_init, METH_KEYWORDS | METH_VARARGS);
    ADD_SLOT_CONV("__alloc__", wrap_allocfunc, cls->tp_alloc, -2);
    ADD_SLOT("__new__", cls->tp_new, METH_KEYWORDS | METH_VARARGS);
    ADD_SLOT("__free__", cls->tp_free, -1);
    ADD_SLOT("__del__", cls->tp_del, -1);
    ADD_SLOT("__finalize__", cls->tp_finalize, -1);

    PyNumberMethods* numbers = cls->tp_as_number;
    if (numbers) {
        ADD_SLOT("__add__", numbers->nb_add, -2);
        ADD_SLOT("__sub__", numbers->nb_subtract, -2);
        ADD_SLOT("__mul__", numbers->nb_multiply, -2);
        ADD_SLOT("__rem__", numbers->nb_remainder, -2);
        ADD_SLOT("__divmod__", numbers->nb_divmod, -2);
        ADD_SLOT_CONV("__pow__", wrap_pow, numbers->nb_power, -3);
        ADD_SLOT("__neg__", numbers->nb_negative, -1);
        ADD_SLOT("__pos__", numbers->nb_positive, -1);
        ADD_SLOT("__abs__", numbers->nb_absolute, -1);
        ADD_SLOT_CONV("__bool__", wrap_inquiry, numbers->nb_bool, -1);
        ADD_SLOT("__invert__", numbers->nb_invert, -1);
        ADD_SLOT("__lshift__", numbers->nb_lshift, -2);
        ADD_SLOT("__rshift__", numbers->nb_rshift, -2);
        ADD_SLOT("__and__", numbers->nb_and, -2);
        ADD_SLOT("__xor__", numbers->nb_xor, -2);
        ADD_SLOT("__or__", numbers->nb_or, -2);
        ADD_SLOT("__int__", numbers->nb_int, -1);
        ADD_SLOT("__float__", numbers->nb_float, -1);
        ADD_SLOT("__iadd__", numbers->nb_inplace_add, -2);
        ADD_SLOT("__isub__", numbers->nb_inplace_subtract, -2);
        ADD_SLOT("__imul__", numbers->nb_inplace_multiply, -2);
        ADD_SLOT("__irem__", numbers->nb_inplace_remainder, -2);
        ADD_SLOT("__ipow__", numbers->nb_inplace_power, -2);
        ADD_SLOT("__ilshift__", numbers->nb_inplace_lshift, -2);
        ADD_SLOT("__irshift__", numbers->nb_inplace_rshift, -2);
        ADD_SLOT("__iand__", numbers->nb_inplace_and, -2);
        ADD_SLOT("__ixor__", numbers->nb_inplace_xor, -2);
        ADD_SLOT("__ior__", numbers->nb_inplace_or, -2);
        ADD_SLOT("__floordiv__", numbers->nb_floor_divide, -2);
        ADD_SLOT("__truediv__", numbers->nb_true_divide, -2);
        ADD_SLOT("__ifloordiv__", numbers->nb_inplace_floor_divide, -2);
        ADD_SLOT("__itruediv__", numbers->nb_inplace_true_divide, -2);
        ADD_SLOT("__index__", numbers->nb_index, -1);
        ADD_SLOT("__matmul__", numbers->nb_matrix_multiply, -2);
        ADD_SLOT("__imatmul__", numbers->nb_inplace_matrix_multiply, -2);
    }

    PySequenceMethods* sequences = cls->tp_as_sequence;
    if (sequences) {
        ADD_SLOT_CONV("__len__", wrap_lenfunc, sequences->sq_length, -1);
        ADD_SLOT("__add__", sequences->sq_concat, -2);
        ADD_SLOT_CONV("__mul__", wrap_ssizeargfunc, sequences->sq_repeat, -2);
        ADD_SLOT_CONV("__getitem__", wrap_ssizeargfunc, sequences->sq_item, -2);
        ADD_SLOT_CONV("__setitem__", wrap_ssizeobjargproc, sequences->sq_ass_item, -3);
        ADD_SLOT_CONV("__contains__", wrap_objobjproc, sequences->sq_contains, -2);
        ADD_SLOT("__iadd__", sequences->sq_inplace_concat, -2);
        ADD_SLOT_CONV("__imul__", wrap_ssizeargfunc, sequences->sq_inplace_repeat, -2);
    }

    PyMappingMethods* mappings = cls->tp_as_mapping;
    if (mappings) {
        ADD_SLOT_CONV("__len__", wrap_lenfunc, mappings->mp_length, -1);
        ADD_SLOT("__getitem__", mappings->mp_subscript, -2);
        ADD_SLOT_CONV("__setitem__", wrap_objobjargproc, mappings->mp_ass_subscript, -3);
    }

    PyAsyncMethods* async = cls->tp_as_async;
    if (async) {
        ADD_SLOT("__await__", async->am_await, -1);
        ADD_SLOT("__aiter__", async->am_aiter, -1);
        ADD_SLOT("__anext__", async->am_anext, -1);
    }

    PyBufferProcs* buffers = cls->tp_as_buffer;
    if (buffers) {
        // TODO ...
    }

    /* Link into each base class's list of subclasses */
    bases = cls->tp_bases;
    Py_ssize_t n = PyTuple_GET_SIZE(bases);
    Py_ssize_t i;
    for (i = 0; i < n; i++) {
        PyObject* base_class_object = PyTuple_GetItem(bases, i);
        PyTypeObject* b = (PyTypeObject*) base_class_object;
        if (PyType_Check(b) && add_subclass(b, cls) < 0) {
        	cls->tp_flags &= ~Py_TPFLAGS_READYING;
        	return -1;
        }
    }

    // done
    cls->tp_flags = cls->tp_flags & ~Py_TPFLAGS_READYING;
    cls->tp_flags = cls->tp_flags | Py_TPFLAGS_READY;

    return 0;

#undef ADD_IF_MISSING
#undef ADD_METHOD
#undef ADD_SLOT
#undef ADD_METHOD_OR_SLOT
}


PyObject* PyObject_Str(PyObject* o) {
    return UPCALL_CEXT_O("PyObject_Str", native_to_java(o));
}

PyObject* PyObject_Repr(PyObject* o) {
    return UPCALL_CEXT_O("PyObject_Repr", native_to_java(o));
}

PyObject* PyObject_Call(PyObject* callable, PyObject* args, PyObject* kwargs) {
    if (kwargs == NULL) {
        kwargs = PyDict_New();
    }
    return UPCALL_CEXT_O("PyObject_Call", native_to_java(callable), native_to_java(args), native_to_java(kwargs));
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

PyObject* PyObject_CallMethod(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    CALL_WITH_VARARGS(args, Py_BuildValue, 3, fmt);
    return UPCALL_CEXT_O("PyObject_CallMethod", native_to_java(object), polyglot_from_string(method, SRC_CS), native_to_java(args));
}

PyObject* PyObject_Type(PyObject* obj) {
    return UPCALL_O(PY_BUILTIN, "type", native_to_java(obj));
}

PyObject* PyObject_GetItem(PyObject* obj, PyObject* key) {
    return UPCALL_O(native_to_java(obj), "__getitem__", native_to_java(key));
}

int PyObject_SetItem(PyObject* obj, PyObject* key, PyObject* value) {
    return UPCALL_CEXT_I("PyObject_SetItem", native_to_java(obj), native_to_java(key), native_to_java(value));
}

PyObject* PyObject_Format(PyObject* obj, PyObject* spec) {
    return UPCALL_O(native_to_java(obj), "__format__", native_to_java(spec));
}

PyObject* PyObject_GetIter(PyObject* obj) {
    return UPCALL_O(PY_BUILTIN, "iter", native_to_java(obj));
}

int PyObject_IsInstance(PyObject* obj, PyObject* typ) {
    return UPCALL_CEXT_I("PyObject_IsInstance", native_to_java(obj), native_to_java(typ));
}

int PyObject_AsFileDescriptor(PyObject* obj) {
    return UPCALL_CEXT_I("PyObject_AsFileDescriptor", native_to_java(obj));
}

int PyObject_Print(PyObject* object, FILE* fd, int flags) {
    void *openFunc, *args, *kwargs;
    void *printfunc, *printargs, *printkwargs;
    void *file;

    openFunc = UPCALL_CEXT_O("PyTruffle_GetBuiltin", polyglot_from_string("open", SRC_CS));
    args = PyTuple_New(1);
    int f = fileno(fd);
    PyTuple_SetItem(args, 0, PyLong_FromLong(f));
    kwargs = PyDict_New();
    int buffering = 0;
    PyDict_SetItemString(kwargs, "buffering", PyLong_FromLong(buffering));
    PyDict_SetItemString(kwargs, "mode", polyglot_from_string("wb", SRC_CS));
    file = PyObject_Call(openFunc, args, kwargs);

    printfunc = UPCALL_CEXT_O("PyTruffle_GetBuiltin", polyglot_from_string("print", SRC_CS));
    printargs = PyTuple_New(1);
    PyTuple_SetItem(printargs, 0, object);
    printkwargs = PyDict_New();
    PyDict_SetItemString(printkwargs, "file", file);
    PyObject_Call(printfunc, printargs, printkwargs);
    return 0;
}

PyObject* PyObject_GetAttrString(PyObject* obj, const char* attr) {
    return UPCALL_CEXT_O("PyObject_GetAttr", native_to_java(obj), polyglot_from_string(attr, SRC_CS));
}

int PyObject_SetAttrString(PyObject* obj, const char* attr, PyObject* value) {
    return UPCALL_CEXT_I("PyObject_SetAttr", native_to_java(obj), polyglot_from_string(attr, SRC_CS), native_to_java(value));
}

int PyObject_HasAttrString(PyObject* obj, const char* attr) {
    return UPCALL_CEXT_I("PyObject_HasAttr", native_to_java(obj), polyglot_from_string(attr, SRC_CS));
}

PyObject* PyObject_GetAttr(PyObject* obj, PyObject* attr) {
    return UPCALL_CEXT_O("PyObject_GetAttr", native_to_java(obj), native_to_java(attr));
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
    return UPCALL_I(PY_BUILTIN, "hash", native_to_java(obj));
}

Py_hash_t PyObject_HashNotImplemented(PyObject* obj) {
    UPCALL_CEXT_VOID("PyObject_HashNotImplemented", native_to_java(obj));
    return -1;
}

int PyObject_IsTrue(PyObject* obj) {
    return UPCALL_CEXT_I("PyObject_IsTrue", native_to_java(obj));
}

int PyObject_Not(PyObject* obj) {
    return PyObject_IsTrue(obj) ? 0 : 1;
}

PyObject * PyObject_RichCompare(PyObject *v, PyObject *w, int op) {
    return UPCALL_CEXT_O("PyObject_RichCompare", native_to_java(v), native_to_java(w), op);
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

PyObject* PyObject_Init(PyObject *op, PyTypeObject *tp) {
    if (op == NULL) {
        return PyErr_NoMemory();
    }
    Py_TYPE(op) = tp;
    _Py_NewReference(op);
    return op;
}

int PyCallable_Check(PyObject *x) {
    return polyglot_as_i32(polyglot_invoke(PY_BUILTIN, "callable", to_java(x)));
}
