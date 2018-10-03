/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

// taken from CPython "Objects/typeobject.c"
typedef struct {
    PyObject_HEAD
    PyTypeObject *type;
    PyObject *obj;
    PyTypeObject *obj_type;
} superobject;

PyTypeObject PyType_Type = PY_TRUFFLE_TYPE("type", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_TYPE_SUBCLASS, sizeof(PyTypeObject));
PyTypeObject PyBaseObject_Type = PY_TRUFFLE_TYPE("object", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, sizeof(PyObject));
PyTypeObject PySuper_Type = PY_TRUFFLE_TYPE("super", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(superobject));

UPCALL_ID(PyType_IsSubtype);
int PyType_IsSubtype(PyTypeObject* a, PyTypeObject* b) {
    return ((int (*)(void* a, void* b))_jls_PyType_IsSubtype)(native_type_to_java(a), native_type_to_java(b));
}

static int add_subclass(PyTypeObject *base, PyTypeObject *type) {
    void* key = (void *) type;
    if (key == NULL) {
        return -1;
    }
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

static PyObject* native_int_to_bool(int res) {
    return res ? Py_True : Py_False;
}

int PyType_Ready(PyTypeObject* cls) {
#define ADD_IF_MISSING(attr, def) if (!(attr)) { attr = def; }
#define ADD_METHOD(m) ADD_METHOD_OR_SLOT(m.ml_name, native_to_java_exported, m.ml_meth, m.ml_flags, NULL, m.ml_doc, convert_method_flags(m.ml_flags))
#define ADD_SLOT(name, meth, flags) ADD_METHOD_OR_SLOT(name, native_to_java_exported, meth, flags, NULL, name, convert_method_flags(flags))
#define ADD_SLOT_CONV(name, result_conversion, meth, flags, wrapper, signature_kind) ADD_METHOD_OR_SLOT(name, result_conversion, meth, flags, wrapper, name, signature_kind)
#define ADD_METHOD_OR_SLOT(name, result_conversion, meth, flags, wrapper, doc, signature_kind)       \
    if (meth) {                                                                             \
        polyglot_invoke(PY_TRUFFLE_CEXT,                                                    \
                       "AddFunction",                                                       \
                       javacls,                                                             \
                       polyglot_from_string((name), SRC_CS),                                \
                       result_conversion != NULL ? truffle_decorate_function(meth, result_conversion) : meth,                  \
                       (wrapper != NULL ? wrapper : get_method_flags_wrapper(flags)),                                     \
                       polyglot_from_string(doc, SRC_CS),                                   \
                       signature_kind,                                                      \
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
    PyDict_SetItemString(native_members, "tp_itemsize", PyLong_FromSsize_t(cls->tp_itemsize));
    PyDict_SetItemString(native_members, "tp_dictoffset", PyLong_FromSsize_t(cls->tp_dictoffset));
    const char* class_name = cls->tp_name;
    PyTypeObject* javacls = polyglot_invoke(PY_TRUFFLE_CEXT,
                                            "PyType_Ready",
                                            // no conversion of cls here, because we
                                            // store this into the PyTypeObject
                                            cls,
                                            native_to_java((PyObject*)metaclass),
                                            native_to_java(bases),
                                            native_to_java(native_members));

    // remember the managed wrapper
    ((PyObject*)cls)->ob_refcnt = javacls;
    if (cls->tp_dict != NULL) {
        javacls->tp_dict = native_to_java(cls->tp_dict);
    } else {
        cls->tp_dict = javacls->tp_dict;
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
                            getter_fun != NULL ? truffle_decorate_function((getter)getter_fun, native_to_java_exported) : native_to_java(Py_None),
                            setter_fun != NULL ? (setter)setter_fun : native_to_java(Py_None),
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
    ADD_SLOT_CONV("__getattr__", native_to_java_exported, cls->tp_getattr, -2, METH_GETATTR, GETATTR_FUNC);
    ADD_SLOT_CONV("__setattr__", NULL, cls->tp_setattr, -3, METH_SETATTR, SETATTR_FUNC);
    ADD_SLOT("__repr__", cls->tp_repr, -1);
    ADD_SLOT_CONV("__hash__", PyLong_FromSsize_t, cls->tp_hash, -1, NULL, DEFAULT);
    ADD_SLOT("__call__", cls->tp_call, METH_KEYWORDS | METH_VARARGS);
    ADD_SLOT("__str__", cls->tp_str, -1);
    ADD_SLOT("__getattr__", cls->tp_getattro, -2);
    ADD_SLOT_CONV("__setattr__", PyLong_FromLong, cls->tp_setattro, -3, NULL, DEFAULT);
    ADD_SLOT("__clear__", cls->tp_clear, -1);
    if (cls->tp_richcompare) {
        ADD_SLOT_CONV("__compare__", native_to_java_exported, cls->tp_richcompare, -3, METH_RICHCMP, RICHCMP_FUNC);
        ADD_SLOT_CONV("__lt__", native_to_java_exported, cls->tp_richcompare, -2, METH_LT, DEFAULT);
        ADD_SLOT_CONV("__le__", native_to_java_exported, cls->tp_richcompare, -2, METH_LE, DEFAULT);
        ADD_SLOT_CONV("__eq__", native_to_java_exported, cls->tp_richcompare, -2, METH_EQ, DEFAULT);
        ADD_SLOT_CONV("__ne__", native_to_java_exported, cls->tp_richcompare, -2, METH_NE, DEFAULT);
        ADD_SLOT_CONV("__gt__", native_to_java_exported, cls->tp_richcompare, -2, METH_GT, DEFAULT);
        ADD_SLOT_CONV("__ge__", native_to_java_exported, cls->tp_richcompare, -2, METH_GE, DEFAULT);
    }
    ADD_SLOT("__iter__", cls->tp_iter, -1);
    ADD_SLOT("__next__", cls->tp_iternext, -1);
    ADD_SLOT("__get__", cls->tp_descr_get, -3);
    ADD_SLOT("__set__", cls->tp_descr_set, -3);
    ADD_SLOT_CONV("__init__", PyLong_FromLong, cls->tp_init, METH_KEYWORDS | METH_VARARGS, NULL, DEFAULT);
    ADD_SLOT_CONV("__alloc__", native_to_java_exported, cls->tp_alloc, -2, METH_ALLOC, ALLOC_FUNC);
    ADD_SLOT("__new__", cls->tp_new, METH_KEYWORDS | METH_VARARGS);
    ADD_SLOT("__free__", cls->tp_free, -1);
    ADD_SLOT("__del__", cls->tp_del, -1);
    ADD_SLOT("__finalize__", cls->tp_finalize, -1);

    PyNumberMethods* numbers = cls->tp_as_number;
    if (numbers) {
        ADD_SLOT("__add__", numbers->nb_add, -2);
        ADD_SLOT_CONV("__radd__", native_to_java_exported, numbers->nb_add, -2, METH_REVERSE, DEFAULT);
        ADD_SLOT("__sub__", numbers->nb_subtract, -2);
        ADD_SLOT_CONV("__rsub__", native_to_java_exported, numbers->nb_subtract, -2, METH_REVERSE, DEFAULT);
        ADD_SLOT("__mul__", numbers->nb_multiply, -2);
        ADD_SLOT("__rem__", numbers->nb_remainder, -2);
        ADD_SLOT("__divmod__", numbers->nb_divmod, -2);
        ADD_SLOT_CONV("__pow__", native_to_java_exported, numbers->nb_power, -3, METH_POW, DEFAULT);
        ADD_SLOT("__neg__", numbers->nb_negative, -1);
        ADD_SLOT("__pos__", numbers->nb_positive, -1);
        ADD_SLOT("__abs__", numbers->nb_absolute, -1);
        ADD_SLOT_CONV("__bool__", native_int_to_bool, numbers->nb_bool, -1, NULL, DEFAULT);
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
        ADD_SLOT_CONV("__len__", PyLong_FromSsize_t, sequences->sq_length, -1, NULL, DEFAULT);
        ADD_SLOT("__add__", sequences->sq_concat, -2);
        ADD_SLOT_CONV("__mul__", PyLong_FromLong, sequences->sq_repeat, -2, METH_SSIZE_ARG, SSIZE_ARG_FUNC);
        ADD_SLOT_CONV("__getitem__", PyLong_FromLong, sequences->sq_item, -2, METH_SSIZE_ARG, SSIZE_ARG_FUNC);
        ADD_SLOT_CONV("__setitem__", PyLong_FromLong, sequences->sq_ass_item, -3, METH_SSIZE_OBJ_ARG, SSIZE_OBJ_ARG_PROC);
        ADD_SLOT_CONV("__contains__", PyLong_FromLong, sequences->sq_contains, -2, NULL, DEFAULT);
        ADD_SLOT("__iadd__", sequences->sq_inplace_concat, -2);
        ADD_SLOT_CONV("__imul__", PyLong_FromLong, sequences->sq_inplace_repeat, -2, METH_SSIZE_ARG, SSIZE_ARG_FUNC);
    }

    PyMappingMethods* mappings = cls->tp_as_mapping;
    if (mappings) {
        ADD_SLOT_CONV("__len__", PyLong_FromSsize_t, mappings->mp_length, -1, NULL, DEFAULT);
        ADD_SLOT("__getitem__", mappings->mp_subscript, -2);
        ADD_SLOT_CONV("__setitem__", PyLong_FromLong, mappings->mp_ass_subscript, -3, NULL, DEFAULT);
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

void PyType_Modified(PyTypeObject* type) {
    // (tfel) I don't think we have to do anything here, since we track MRO
    // changes separately, anyway
}
