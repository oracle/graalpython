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

#undef RICHCMP_WRAPPER
#define RICHCMP_WRAPPER(NAME, OP)                                       \
    static PyObject* wrap_richcmpfunc_##NAME(richcmpfunc f,             \
                                             PyObject* a,               \
                                             PyObject* b) {             \
        return f(a, b, OP);                                             \
    }

RICHCMP_WRAPPER(lt, Py_LT)
RICHCMP_WRAPPER(le, Py_LE)
RICHCMP_WRAPPER(eq, Py_EQ)
RICHCMP_WRAPPER(ne, Py_NE)
RICHCMP_WRAPPER(gt, Py_GT)
RICHCMP_WRAPPER(ge, Py_GE)

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

static PyObject* wrap_nb_bool(inquiry f, PyObject* a) {
    return f(a) ? Py_True : Py_False;
}

/* very special case: operator '**' has an optional third arg */
static PyObject* wrap_pow(ternaryfunc f, ...) {
    int nargs = polyglot_get_arg_count();
    switch(nargs) {
    case 3:
        return f(polyglot_get_arg(1), polyglot_get_arg(2), Py_None);
    case 4:
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

static PyObject* wrap_reverse_binop(binaryfunc f, PyObject* a, PyObject* b) {
    return f(b, a);
}

static void
inherit_special(PyTypeObject *type, PyTypeObject *base)
{

    /* Copying basicsize is connected to the GC flags */
    if (!(type->tp_flags & Py_TPFLAGS_HAVE_GC) &&
        (base->tp_flags & Py_TPFLAGS_HAVE_GC) &&
        (!type->tp_traverse && !type->tp_clear)) {
        type->tp_flags |= Py_TPFLAGS_HAVE_GC;
        if (type->tp_traverse == NULL)
            type->tp_traverse = base->tp_traverse;
        if (type->tp_clear == NULL)
            type->tp_clear = base->tp_clear;
    }
    {
        /* The condition below could use some explanation.
           It appears that tp_new is not inherited for static types
           whose base class is 'object'; this seems to be a precaution
           so that old extension types don't suddenly become
           callable (object.__new__ wouldn't insure the invariants
           that the extension type's own factory function ensures).
           Heap types, of course, are under our control, so they do
           inherit tp_new; static extension types that specify some
           other built-in type as the default also
           inherit object.__new__. */
        if (base != &PyBaseObject_Type ||
            (type->tp_flags & Py_TPFLAGS_HEAPTYPE)) {
            if (type->tp_new == NULL)
                type->tp_new = base->tp_new;
        }
    }
    if (type->tp_basicsize == 0)
        type->tp_basicsize = base->tp_basicsize;

    /* Copy other non-function slots */

#undef COPYVAL
#define COPYVAL(SLOT) \
    if (type->SLOT == 0) type->SLOT = base->SLOT

    COPYVAL(tp_itemsize);
    COPYVAL(tp_weaklistoffset);
    COPYVAL(tp_dictoffset);

    /* Setup fast subclass flags */
    if (PyType_IsSubtype(base, (PyTypeObject*)PyExc_BaseException))
        type->tp_flags |= Py_TPFLAGS_BASE_EXC_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyType_Type))
        type->tp_flags |= Py_TPFLAGS_TYPE_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyLong_Type))
        type->tp_flags |= Py_TPFLAGS_LONG_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyBytes_Type))
        type->tp_flags |= Py_TPFLAGS_BYTES_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyUnicode_Type))
        type->tp_flags |= Py_TPFLAGS_UNICODE_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyTuple_Type))
        type->tp_flags |= Py_TPFLAGS_TUPLE_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyList_Type))
        type->tp_flags |= Py_TPFLAGS_LIST_SUBCLASS;
    else if (PyType_IsSubtype(base, &PyDict_Type))
        type->tp_flags |= Py_TPFLAGS_DICT_SUBCLASS;
}

// TODO support member flags other than READONLY
UPCALL_ID(AddMember);
static void add_member(PyTypeObject* cls, PyObject* type_dict, PyObject* mname, int mtype, Py_ssize_t moffset, int mflags, char* mdoc) {
	UPCALL_CEXT_VOID(_jls_AddMember,
			cls,
		    native_to_java(type_dict),
		    native_to_java(mname),
		    mtype,
		    moffset,
		    native_to_java(((mflags & READONLY) == 0) ? Py_True : Py_False),
		    polyglot_from_string(mdoc ? mdoc : "", SRC_CS)
	);
}

static void add_method_or_slot(PyTypeObject* cls, PyObject* type_dict, char* name, void* meth, void* clanding, int flags, char* doc) {
	polyglot_invoke(PY_TRUFFLE_CEXT,
			"AddFunction",
			cls,
			native_to_java(type_dict),
			polyglot_from_string((name), SRC_CS),
			(meth),
			(clanding),
			get_method_flags_wrapper(flags),
			polyglot_from_string(doc, SRC_CS),
			(flags) > 0 && ((flags) & METH_CLASS) != 0,
			(flags) > 0 && ((flags) & METH_STATIC) != 0);
}

#define ADD_MEMBER(__javacls__, __tpdict__, __mname__, __mtype__, __moffset__, __mflags__, __mdoc__)     \
	add_member((__javacls__), (__tpdict__), (__mname__), (__mtype__), (__moffset__), (__mflags__), (__mdoc__))


UPCALL_ID(PyTruffle_Type_Slots);
UPCALL_ID(PyTruffle_Compute_Mro);
int PyType_Ready(PyTypeObject* cls) {
#define RETURN_ERROR(__type__) \
	do { \
      	(__type__)->tp_flags &= ~Py_TPFLAGS_READYING; \
        return -1; \
	} while(0)

#define ADD_IF_MISSING(attr, def) if (!(attr)) { attr = def; }
#define ADD_METHOD(m) ADD_METHOD_OR_SLOT(m.ml_name, get_method_flags_cwrapper(m.ml_flags), m.ml_meth, m.ml_flags, m.ml_doc)
#define ADD_SLOT(name, meth, flags) ADD_METHOD_OR_SLOT(name, get_method_flags_cwrapper(flags), meth, flags, name)
#define ADD_SLOT_CONV(name, clanding, meth, flags) ADD_METHOD_OR_SLOT(name, clanding, meth, flags, name)
#define ADD_METHOD_OR_SLOT(__name__, __clanding__, __meth__, __flags__, __doc__) \
	if (__meth__) { \
		add_method_or_slot(cls, dict, (__name__), (__meth__), (__clanding__), (__flags__), (__doc__)); \
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
    } else {
        if (!metaclass) {
            metaclass = Py_TYPE(base);
        }
    }
    if (!metaclass) {
        metaclass = &PyType_Type;
    }
    cls->tp_base = base;
    Py_TYPE(cls) = metaclass;

    /* Initialize the base class */
    if (base != NULL && !(base->tp_flags % Py_TPFLAGS_READY)) {
        if (PyType_Ready(base) < 0) {
        	RETURN_ERROR(cls);
        }
    }

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
    } else {
    	// we need to resolve pointers to Python classes
    	Py_ssize_t n_bases = PyObject_Length(bases);
    	for(Py_ssize_t i=0; i < n_bases; i++) {
    		PyTuple_SetItem(bases, i, native_to_java(PyTuple_GetItem(bases, i)));
    	}
    }
    cls->tp_bases = bases;

    /* Initialize tp_dict */
    PyObject* dict = cls->tp_dict;
    if (dict == NULL) {
        dict = PyDict_New();
        if (dict == NULL) {
        	RETURN_ERROR(cls);
        }
        cls->tp_dict = dict;
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
        	ADD_MEMBER(cls, dict, polyglot_from_string(member.name, SRC_CS), member.type, member.offset, member.flags, member.doc);
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
                            cls,
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

    /* initialize mro */
    cls->tp_mro = UPCALL_CEXT_O(_jls_PyTruffle_Compute_Mro, cls, polyglot_from_string(cls->tp_name, SRC_CS));

    /* Inherit special flags from dominant base */
    if (cls->tp_base != NULL)
        inherit_special(cls, cls->tp_base);

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
    if (cls->tp_richcompare) {
        ADD_SLOT_CONV("__compare__", wrap_richcmpfunc, cls->tp_richcompare, -3);
        ADD_SLOT_CONV("__lt__", wrap_richcmpfunc_lt, cls->tp_richcompare, -2);
        ADD_SLOT_CONV("__le__", wrap_richcmpfunc_le, cls->tp_richcompare, -2);
        ADD_SLOT_CONV("__eq__", wrap_richcmpfunc_eq, cls->tp_richcompare, -2);
        ADD_SLOT_CONV("__ne__", wrap_richcmpfunc_ne, cls->tp_richcompare, -2);
        ADD_SLOT_CONV("__gt__", wrap_richcmpfunc_gt, cls->tp_richcompare, -2);
        ADD_SLOT_CONV("__ge__", wrap_richcmpfunc_ge, cls->tp_richcompare, -2);
    }
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
        ADD_SLOT_CONV("__radd__", wrap_reverse_binop, numbers->nb_add, -2);
        ADD_SLOT("__sub__", numbers->nb_subtract, -2);
        ADD_SLOT_CONV("__rsub__", wrap_reverse_binop, numbers->nb_subtract, -2);
        ADD_SLOT("__mul__", numbers->nb_multiply, -2);
        ADD_SLOT("__rem__", numbers->nb_remainder, -2);
        ADD_SLOT("__divmod__", numbers->nb_divmod, -2);
        ADD_SLOT_CONV("__pow__", wrap_pow, numbers->nb_power, -3);
        ADD_SLOT("__neg__", numbers->nb_negative, -1);
        ADD_SLOT("__pos__", numbers->nb_positive, -1);
        ADD_SLOT("__abs__", numbers->nb_absolute, -1);
        ADD_SLOT_CONV("__bool__", wrap_nb_bool, numbers->nb_bool, -1);
        ADD_SLOT("__invert__", numbers->nb_invert, -1);
        ADD_SLOT("__lshift__", numbers->nb_lshift, -2);
        ADD_SLOT_CONV("__rlshift__", wrap_reverse_binop, numbers->nb_lshift, -2);
        ADD_SLOT("__rshift__", numbers->nb_rshift, -2);
        ADD_SLOT("__and__", numbers->nb_and, -2);
        ADD_SLOT_CONV("__rand__", wrap_reverse_binop, numbers->nb_and, -2);
        ADD_SLOT("__xor__", numbers->nb_xor, -2);
        ADD_SLOT_CONV("__rxor__", wrap_reverse_binop, numbers->nb_xor, -2);
        ADD_SLOT("__or__", numbers->nb_or, -2);
        ADD_SLOT_CONV("__ror__", wrap_reverse_binop, numbers->nb_or, -2);
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

    // process inherited slots
    // CPython doesn't do that in 'PyType_Ready' but we must because a native type can inherit
    // dynamic slots from a managed Python class. Since the managed Python class may be created
    // when the C API is not loaded, we need to do that later.
    PyObject* inherited_slots_tuple = UPCALL_CEXT_O(_jls_PyTruffle_Type_Slots, native_to_java((PyObject*)cls));
    if(inherited_slots_tuple != NULL) {
    	PyTruffle_Type_AddSlots(cls, inherited_slots_tuple);
    }

    /* Initialize this classes' tp_subclasses dict. This is necessary because our managed classes won't do. */
    cls->tp_subclasses = PyDict_New();

    /* Link into each base class's list of subclasses */
    bases = cls->tp_bases;
    Py_ssize_t n = PyTuple_GET_SIZE(bases);
    Py_ssize_t i;
    for (i = 0; i < n; i++) {
        PyObject* base_class_object = PyTuple_GetItem(bases, i);
        PyTypeObject* b = (PyTypeObject*) base_class_object;
        if (PyType_Check(b) && add_subclass(b, cls) < 0) {
        	RETURN_ERROR(cls);
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

UPCALL_ID(PyTruffle_Type_Modified);
void PyType_Modified(PyTypeObject* type) {
	UPCALL_CEXT_VOID(_jls_PyTruffle_Type_Modified, native_type_to_java(type), polyglot_from_string(type->tp_name, SRC_CS), native_to_java(type->tp_mro));
}

MUST_INLINE static int valid_identifier(PyObject *s) {
    if (!PyUnicode_Check(s)) {
        PyErr_Format(PyExc_TypeError,
                     "__slots__ items must be strings, not '%.200s'",
                     Py_TYPE(s)->tp_name);
        return 0;
    }
    return 1;
}

/* Add get-set descriptors for slots provided in 'slotsTuple'. */
Py_ssize_t PyTruffle_Type_AddSlots(PyTypeObject* cls, PyObject* slotsTuple) {
    int i;
    Py_ssize_t cur_offset = cls->tp_basicsize;
    Py_ssize_t dictoffset = cls->tp_dictoffset;
    Py_ssize_t slotLen = PyTuple_Size(slotsTuple);
    PyObject* slot;

    for(i = 0; i < slotLen; i++) {
    	slot = PyTuple_GetItem(slotsTuple, i);
    	// note: no flags and no doc (see typeobject.c in function 'type_new')
    	ADD_MEMBER(cls, cls->tp_dict, slot, T_OBJECT_EX, cur_offset, 0, NULL);
    	cur_offset += sizeof(PyObject*);
    	dictoffset += sizeof(PyObject*);
    }
    // only update if there was a dictoffset
    if (cls->tp_dictoffset != 0) {
    	cls->tp_dictoffset = dictoffset;
    }
    cls->tp_basicsize = cur_offset;
    return cur_offset;
}
