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

PyTypeObject _PyNone_Type = {
    PyVarObject_HEAD_INIT(&PyType_Type, 0)
    "NoneType",
    0,
    0,
    none_dealloc,       /*tp_dealloc*/ /*never called*/
    0,                  /*tp_print*/
    0,                  /*tp_getattr*/
    0,                  /*tp_setattr*/
    0,                  /*tp_reserved*/
    none_repr,          /*tp_repr*/
    &none_as_number,    /*tp_as_number*/
    0,                  /*tp_as_sequence*/
    0,                  /*tp_as_mapping*/
    0,                  /*tp_hash */
    0,                  /*tp_call */
    0,                  /*tp_str */
    0,                  /*tp_getattro */
    0,                  /*tp_setattro */
    0,                  /*tp_as_buffer */
    Py_TPFLAGS_DEFAULT, /*tp_flags */
    0,                  /*tp_doc */
    0,                  /*tp_traverse */
    0,                  /*tp_clear */
    0,                  /*tp_richcompare */
    0,                  /*tp_weaklistoffset */
    0,                  /*tp_iter */
    0,                  /*tp_iternext */
    0,                  /*tp_methods */
    0,                  /*tp_members */
    0,                  /*tp_getset */
    0,                  /*tp_base */
    0,                  /*tp_dict */
    0,                  /*tp_descr_get */
    0,                  /*tp_descr_set */
    0,                  /*tp_dictoffset */
    0,                  /*tp_init */
    0,                  /*tp_alloc */
    none_new,           /*tp_new */
};

PyObject _Py_NoneStruct = {
  _PyObject_EXTRA_INIT
  1, &_PyNone_Type
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
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyObject_Size", to_java(o));
}

int PyType_Ready(PyTypeObject* cls) {
#define ADD_IF_MISSING(attr, def) if (!(attr)) { attr = def; }
#define ADD_METHOD(m) ADD_METHOD_OR_SLOT(m.ml_name, m.ml_meth, m.ml_flags, m.ml_doc)
#define ADD_SLOT(name, meth, flags) ADD_METHOD_OR_SLOT(name, meth, flags, name)
#define ADD_METHOD_OR_SLOT(name, meth, flags, doc)                      \
    if (meth) {                                                         \
        truffle_invoke(PY_TRUFFLE_CEXT,                                 \
                       "AddFunction",                                   \
                       javacls,                                         \
                       truffle_read_string(name),                       \
                       truffle_address_to_function(meth),               \
                       get_method_flags_wrapper(flags),                 \
                       truffle_read_string(doc),                        \
                       (flags) > 0 && ((flags) & METH_CLASS) != 0,      \
                       (flags) > 0 && ((flags) & METH_STATIC) != 0);    \
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

    PyObject* javacls = truffle_invoke(PY_TRUFFLE_CEXT,
                                       "PyType_Ready",
                                       // no conversion of cls here, because we
                                       // store this into the PyTypeObject
                                       cls,
                                       to_java_type(metaclass),
                                       to_java_type(base),
                                       truffle_read_string(cls->tp_name),
                                       truffle_read_string(cls->tp_doc));
    // store the back reference
    marry_objects(cls, javacls);

    // https://docs.python.org/3/c-api/typeobj.html#c.PyTypeObject.tp_name
    const char* lastDot = strrchr(cls->tp_name, '.');
    if (lastDot) {
        truffle_write(javacls, "__module__", truffle_read_string(lastDot + 1));
    }

    // https://docs.python.org/3/c-api/typeobj.html#c.PyTypeObject.tp_doc
    if (cls->tp_doc) {
        truffle_write(javacls, "__doc__", truffle_read_string(cls->tp_doc));
    }

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
            truffle_invoke(PY_TRUFFLE_CEXT,
                           "AddMember",
                           javacls,
                           truffle_read_string(member.name),
                           member.type,
                           member.offset,
                           // TODO: support other flags
                           ((member.flags & READONLY) == 0) ? Py_True : Py_False,
                           truffle_read_string(member.doc));
            member = members[++i];
        }
    }

    PyGetSetDef* getsets = cls->tp_getset;
    if (getsets) {
        int i = 0;
        PyGetSetDef getset = getsets[i];
        while (getset.name != NULL) {
            truffle_invoke(PY_TRUFFLE_CEXT,
                           "AddGetSet",
                           javacls,
                           truffle_read_string(getset.name),
                           truffle_address_to_function(getset.get),
                           truffle_address_to_function(getset.set),
                           truffle_read_string(getset.doc),
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
    ADD_SLOT("__getattr__", cls->tp_getattr, -2);
    ADD_SLOT("__setattr__", cls->tp_setattr, -3);
    ADD_SLOT("__repr__", cls->tp_repr, -1);
    ADD_SLOT("__hash__", cls->tp_hash, -1);
    ADD_SLOT("__call__", cls->tp_call, METH_KEYWORDS | METH_VARARGS);
    ADD_SLOT("__str__", cls->tp_str, -1);
    ADD_SLOT("__getattr__", cls->tp_getattro, -2);
    ADD_SLOT("__setattr__", cls->tp_getattro, -3);
    ADD_SLOT("__clear__", cls->tp_clear, -1);
    ADD_SLOT("__compare__", cls->tp_richcompare, -3);
    ADD_SLOT("__iter__", cls->tp_iter, -1);
    ADD_SLOT("__next__", cls->tp_iternext, -1);
    ADD_SLOT("__get__", cls->tp_descr_get, -3);
    ADD_SLOT("__set__", cls->tp_descr_set, -3);
    ADD_SLOT("__init__", cls->tp_init, METH_KEYWORDS | METH_VARARGS);
    ADD_SLOT("__alloc__", cls->tp_alloc, -2);
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
        ADD_SLOT("__pow__", numbers->nb_power, -2);
        ADD_SLOT("__neg__", numbers->nb_negative, -1);
        ADD_SLOT("__pos__", numbers->nb_positive, -1);
        ADD_SLOT("__abs__", numbers->nb_absolute, -1);
        ADD_SLOT("__bool__", numbers->nb_bool, -1);
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
        ADD_SLOT("__len__", sequences->sq_length, -1);
        ADD_SLOT("__add__", sequences->sq_concat, -2);
        ADD_SLOT("__mul__", sequences->sq_repeat, -2);
        ADD_SLOT("__getitem__", sequences->sq_item, -2);
        ADD_SLOT("__setitem__", sequences->sq_ass_item, -3);
        ADD_SLOT("__contains__", sequences->sq_contains, -2);
        ADD_SLOT("__iadd__", sequences->sq_inplace_concat, -2);
        ADD_SLOT("__imul__", sequences->sq_inplace_repeat, -2);
    }

    PyMappingMethods* mappings = cls->tp_as_mapping;
    if (mappings) {
        ADD_SLOT("__len__", mappings->mp_length, -1);
        ADD_SLOT("__getitem__", mappings->mp_subscript, -2);
        ADD_SLOT("__setitem__", mappings->mp_subscript, -3);
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
    return to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyObject_Str", to_java(o)));
}

PyObject* PyObject_Repr(PyObject* o) {
    return to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyObject_Repr", to_java(o)));
}

PyObject* PyObject_Call(PyObject* callable, PyObject* args, PyObject* kwargs) {
    if (kwargs == NULL) {
        kwargs = PyDict_New();
    }
    return to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyObject_Call", to_java(callable), to_java(args), to_java(kwargs)));
}

PyObject* PyObject_CallObject(PyObject* callable, PyObject* args) {
    return PyObject_Call(callable, args, PyDict_New());
}

PyObject* PyTruffle_Object_CallFunction(PyObject* callable, const char* fmt, int c, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9) {
    PyObject* args = PyTruffle_BuildValue(fmt, v0, v1, v2, v3, v4, v5, v6, v7, v8, v9);
    if (strlen(fmt) < 2) {
        PyObject* singleArg = args;
        args = PyTuple_New(strlen(fmt));
        if (strlen(fmt) == 1) {
            PyTuple_SetItem(args, 0, singleArg);
        }
    }
    return PyObject_CallObject(callable, args);
}

PyObject* PyTruffle_Object_CallMethod(PyObject* object, const char* method, const char* fmt, int c, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9) {
    PyObject* args = PyTruffle_BuildValue(fmt, v0, v1, v2, v3, v4, v5, v6, v7, v8, v9);
    return to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyObject_CallMethod", to_java(object), truffle_read_string(method), to_java(args)));
}

PyObject* PyObject_Type(PyObject* obj) {
    return to_sulong(truffle_invoke(PY_BUILTIN, "type", to_java(obj)));
}

PyObject* PyObject_GetItem(PyObject* obj, PyObject* key) {
    return to_sulong(truffle_invoke(to_java(obj), "__getitem__", to_java(key)));
}

int PyObject_SetItem(PyObject* obj, PyObject* key, PyObject* value) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyObject_SetItem", to_java(obj), to_java(key), to_java(value));
}

PyObject* PyObject_Format(PyObject* obj, PyObject* spec) {
    return to_sulong(truffle_invoke(to_java(obj), "__format__", to_java(spec)));
}

PyObject* PyObject_GetIter(PyObject* obj) {
    return to_sulong(truffle_invoke(PY_BUILTIN, "iter", to_java(obj)));
}

int PyObject_IsInstance(PyObject* obj, PyObject* typ) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyObject_IsInstance", to_java(obj), to_java(typ));
}

int PyObject_AsFileDescriptor(PyObject* obj) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyObject_AsFileDescriptor", to_java(obj));
}

int PyObject_Print(PyObject* object, FILE* fd, int flags) {
    void *openFunc, *args, *kwargs;
    void *printfunc, *printargs, *printkwargs;
    void *file;

    openFunc = truffle_read(PY_BUILTIN, "open");
    args = truffle_invoke(PY_TRUFFLE_CEXT, "PyTuple_New", 1);
    int f = fileno(fd);
    truffle_invoke(PY_TRUFFLE_CEXT, "PyTuple_SetItem", args, 0, f);
    kwargs = truffle_invoke(PY_BUILTIN, "dict");
    int buffering = 0;
    truffle_invoke(kwargs, "__setitem__", truffle_read_string("buffering"), buffering);
    truffle_write(kwargs, "mode", truffle_read_string("wb"));
    file = truffle_invoke(PY_TRUFFLE_CEXT, "PyObject_Call", openFunc, args, kwargs);

    printfunc = truffle_read(PY_BUILTIN, "print");
    printargs = truffle_invoke(PY_TRUFFLE_CEXT, "PyTuple_New", 1);
    truffle_invoke(PY_TRUFFLE_CEXT, "PyTuple_SetItem", printargs, 0, to_java(object));
    printkwargs = truffle_invoke(PY_BUILTIN, "dict");
    truffle_write(printkwargs, "file", file);
    truffle_invoke(PY_TRUFFLE_CEXT, "PyObject_Call", printfunc, printargs, printkwargs);
    return 0;
}

PyObject* PyObject_GetAttrString(PyObject* obj, const char* attr) {
    return to_sulong(truffle_read(to_java(obj), truffle_read_string(attr)));
}

int PyObject_SetAttrString(PyObject* obj, const char* attr, PyObject* value) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyObject_SetAttr", to_java(obj), truffle_read_string(attr), to_java(value));
}

int PyObject_HasAttrString(PyObject* obj, const char* attr) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyObject_HasAttr", to_java(obj), truffle_read_string(attr));
}

PyObject* PyObject_GetAttr(PyObject* obj, PyObject* attr) {
    return to_sulong(truffle_invoke(PY_BUILTIN, "getattr", to_java(obj), to_java(attr)));
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
    return truffle_invoke_i(PY_BUILTIN, "hash", to_java(obj));
}

Py_hash_t PyObject_HashNotImplemented(PyObject* obj) {
    truffle_invoke(PY_TRUFFLE_CEXT, "PyObject_HashNotImplemented", to_java(obj));
    return -1;
}

int PyObject_IsTrue(PyObject* obj) {
    return truffle_invoke_i(PY_TRUFFLE_CEXT, "PyObject_IsTrue", to_java(obj));
}

int PyObject_Not(PyObject* obj) {
    return PyObject_IsTrue(obj) ? 0 : 1;
}

PyObject * PyObject_RichCompare(PyObject *v, PyObject *w, int op) {
    PyObject* result = truffle_invoke(PY_TRUFFLE_CEXT, "PyObject_RichCompare", to_java(v), to_java(w), op, ERROR_MARKER);
    if (result == ERROR_MARKER) {
        return NULL;
    } else {
        return to_sulong(result);
    }
}

int PyObject_RichCompareBool(PyObject *v, PyObject *w, int op) {
    PyObject* res = PyObject_RichCompare(v, w, op);
    if (res == NULL) {
        return -1;
    } else {
        return PyObject_IsTrue(res);
    }
}
