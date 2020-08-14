/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

static void PyTruffle_Type_AddSlots(PyTypeObject* cls, PyGetSetDef** getsets, uint64_t n_getsets, PyMemberDef** members, uint64_t n_members);

static void object_dealloc(PyObject *self) {
    Py_TYPE(self)->tp_free(self);
}

PyTypeObject PyType_Type = PY_TRUFFLE_TYPE("type", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_TYPE_SUBCLASS, sizeof(PyHeapTypeObject));
PyTypeObject PyBaseObject_Type = PY_TRUFFLE_TYPE_WITH_ALLOC("object", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, sizeof(PyObject), 0, object_dealloc, PyObject_Del);
PyTypeObject PySuper_Type = PY_TRUFFLE_TYPE("super", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE, sizeof(superobject));

typedef int (*type_issubtype_fun_t)(PyTypeObject*, PyTypeObject*);
UPCALL_TYPED_ID(PyType_IsSubtype, type_issubtype_fun_t);
int PyType_IsSubtype(PyTypeObject* a, PyTypeObject* b) {
    return _jls_PyType_IsSubtype(a, b);
}

PyObject* PyType_GenericAlloc(PyTypeObject* cls, Py_ssize_t nitems) {
    PyObject *obj;
    const size_t size = _PyObject_VAR_SIZE(cls, nitems+1);

    if (PyType_IS_GC(cls))
        obj = _PyObject_GC_Malloc(size);
    else
        obj = (PyObject *)PyObject_MALLOC(size);

    if (obj == NULL)
        return PyErr_NoMemory();

    memset(obj, '\0', size);

    if (cls->tp_flags & Py_TPFLAGS_HEAPTYPE)
        Py_INCREF(cls);

    if (cls->tp_itemsize == 0)
        (void)PyObject_INIT(obj, cls);
    else
        (void) PyObject_INIT_VAR((PyVarObject *)obj, cls, nitems);

    return obj;
}

PyObject* PyType_GenericNew(PyTypeObject* cls, PyObject* args, PyObject* kwds) {
    PyObject* newInstance = cls->tp_alloc(cls, 0);
    // TODO(fa): CPython does not do it here; verify if that's correct
    Py_TYPE(newInstance) = cls;
    return newInstance;
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

/*
 * finds the beginning of the docstring's introspection signature.
 * if present, returns a pointer pointing to the first '('.
 * otherwise returns NULL.
 *
 * doesn't guarantee that the signature is valid, only that it
 * has a valid prefix.  (the signature must also pass skip_signature.)
 */
static const char *
find_signature(const char *name, const char *doc)
{
    const char *dot;
    size_t length;

    if (!doc)
        return NULL;

    assert(name != NULL);

    /* for dotted names like classes, only use the last component */
    dot = strrchr(name, '.');
    if (dot)
        name = dot + 1;

    length = strlen(name);
    if (strncmp(doc, name, length))
        return NULL;
    doc += length;
    if (*doc != '(')
        return NULL;
    return doc;
}

typedef void (*trace_type_fun_t)(PyTypeObject* type, void* type_name);
UPCALL_TYPED_ID(PyTruffle_Trace_Type, trace_type_fun_t);
static void pytruffle_trace_type(PyTypeObject* type) {
    _jls_PyTruffle_Trace_Type(type, type->tp_name != NULL ? polyglot_from_string(type->tp_name, SRC_CS) : NULL);
}

#define SIGNATURE_END_MARKER         ")\n--\n\n"
#define SIGNATURE_END_MARKER_LENGTH  6
/*
 * skips past the end of the docstring's instrospection signature.
 * (assumes doc starts with a valid signature prefix.)
 */
static const char *
skip_signature(const char *doc)
{
    while (*doc) {
        if ((*doc == *SIGNATURE_END_MARKER) &&
            !strncmp(doc, SIGNATURE_END_MARKER, SIGNATURE_END_MARKER_LENGTH))
            return doc + SIGNATURE_END_MARKER_LENGTH;
        if ((*doc == '\n') && (doc[1] == '\n'))
            return NULL;
        doc++;
    }
    return NULL;
}

static const char *
_PyType_DocWithoutSignature(const char *name, const char *internal_doc)
{
    const char *doc = find_signature(name, internal_doc);

    if (doc) {
        doc = skip_signature(doc);
        if (doc)
            return doc;
        }
    return internal_doc;
}

UPCALL_ID(PyTruffle_Type_Modified);
void PyType_Modified(PyTypeObject* type) {
	UPCALL_CEXT_VOID(_jls_PyTruffle_Type_Modified, native_type_to_java(type), polyglot_from_string(type->tp_name, SRC_CS), native_to_java(type->tp_mro));
}

static void inherit_special(PyTypeObject *type, PyTypeObject *base) {

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

static void inherit_slots(PyTypeObject *type, PyTypeObject *base) {
    PyTypeObject *basebase;

#undef SLOTDEFINED
#undef COPYSLOT
#undef COPYNUM
#undef COPYSEQ
#undef COPYMAP
#undef COPYBUF

#define SLOTDEFINED(SLOT) \
    (base->SLOT != 0 && \
     (basebase == NULL || base->SLOT != basebase->SLOT))

#define COPYSLOT(SLOT) \
    if (!type->SLOT && SLOTDEFINED(SLOT)) type->SLOT = base->SLOT

#define COPYASYNC(SLOT) COPYSLOT(tp_as_async->SLOT)
#define COPYNUM(SLOT) COPYSLOT(tp_as_number->SLOT)
#define COPYSEQ(SLOT) COPYSLOT(tp_as_sequence->SLOT)
#define COPYMAP(SLOT) COPYSLOT(tp_as_mapping->SLOT)
#define COPYBUF(SLOT) COPYSLOT(tp_as_buffer->SLOT)

    basebase = base->tp_base;

    COPYSLOT(tp_dealloc);
    if (type->tp_getattr == NULL && type->tp_getattro == NULL) {
        type->tp_getattr = base->tp_getattr;
        type->tp_getattro = base->tp_getattro;
    }
    if (type->tp_setattr == NULL && type->tp_setattro == NULL) {
        type->tp_setattr = base->tp_setattr;
        type->tp_setattro = base->tp_setattro;
    }
    {
        /* Always inherit tp_vectorcall_offset to support PyVectorcall_Call().
         * If _Py_TPFLAGS_HAVE_VECTORCALL is not inherited, then vectorcall
         * won't be used automatically. */
        COPYSLOT(tp_vectorcall_offset);

        /* Inherit _Py_TPFLAGS_HAVE_VECTORCALL for non-heap types
        * if tp_call is not overridden */
        if (!type->tp_call &&
            (base->tp_flags & _Py_TPFLAGS_HAVE_VECTORCALL) &&
            !(type->tp_flags & Py_TPFLAGS_HEAPTYPE))
        {
            type->tp_flags |= _Py_TPFLAGS_HAVE_VECTORCALL;
        }
        /* COPYSLOT(tp_call); */
    }

    if ((type->tp_flags & Py_TPFLAGS_HAVE_FINALIZE) &&
        (base->tp_flags & Py_TPFLAGS_HAVE_FINALIZE)) {
        COPYSLOT(tp_finalize);
    }
    if ((type->tp_flags & Py_TPFLAGS_HAVE_GC) ==
        (base->tp_flags & Py_TPFLAGS_HAVE_GC)) {
        /* They agree about gc. */
        COPYSLOT(tp_free);
    }
    else if ((type->tp_flags & Py_TPFLAGS_HAVE_GC) &&
             type->tp_free == NULL &&
             base->tp_free == PyObject_Free) {
        /* A bit of magic to plug in the correct default
         * tp_free function when a derived class adds gc,
         * didn't define tp_free, and the base uses the
         * default non-gc tp_free.
         */
        type->tp_free = PyObject_GC_Del;
    }
    /* else they didn't agree about gc, and there isn't something
     * obvious to be done -- the type is on its own.
     */
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
		    mdoc ? polyglot_from_string(mdoc, SRC_CS) : native_to_java(Py_None)
	);
}

void add_getset(PyTypeObject* cls, char* name, getter getter_fun, setter setter_fun, char* doc, void* closure) {
	polyglot_invoke(PY_TRUFFLE_CEXT,
                    "AddGetSet",
                    cls,
                    polyglot_from_string(name, SRC_CS),
                    getter_fun != NULL ? (getter) native_pointer_to_java(getter_fun) : to_java(Py_None),
                    setter_fun != NULL ? (setter) native_pointer_to_java(setter_fun) : to_java(Py_None),
                    doc ? polyglot_from_string(doc, SRC_CS) : native_to_java(Py_None),
                    /* do not convert the closure, it is handed to the getter and setter as-is */
                    closure);
}

static void add_method_or_slot(PyTypeObject* cls, PyObject* type_dict, char* name, void* result_conversion, void* meth, int flags, void* signature, char* doc) {
        polyglot_invoke(PY_TRUFFLE_CEXT,
                       "AddFunction",
                       cls,
                       native_to_java(type_dict),
                       polyglot_from_string(name, SRC_CS),
                       native_pointer_to_java(result_conversion != NULL ? pytruffle_decorate_function(native_pointer_to_java(meth), result_conversion) : native_pointer_to_java(meth)),
                       (signature != NULL ? signature : get_method_flags_wrapper(flags)),
                       doc ? polyglot_from_string(doc, SRC_CS) : native_to_java(Py_None),
                       (flags) > 0 && ((flags) & METH_CLASS) != 0,
                       (flags) > 0 && ((flags) & METH_STATIC) != 0);
}

#define ADD_MEMBER(__javacls__, __tpdict__, __mname__, __mtype__, __moffset__, __mflags__, __mdoc__)     \
	add_member((__javacls__), (__tpdict__), (__mname__), (__mtype__), (__moffset__), (__mflags__), (__mdoc__))


#define ADD_GETSET(__javacls__, __name__, __getter__, __setter__, __doc__, __closure__)     \
	add_getset((__javacls__), (__name__), (__getter__), (__setter__), (__doc__), (__closure__))


UPCALL_ID(PyTruffle_Get_Inherited_Native_Slots);
UPCALL_ID(PyTruffle_Compute_Mro);
int PyType_Ready(PyTypeObject* cls) {
#define RETURN_ERROR(__type__) \
	do { \
      	(__type__)->tp_flags &= ~Py_TPFLAGS_READYING; \
      	Py_DECREF((__type__)); \
        return -1; \
	} while(0)

#define ADD_IF_MISSING(attr, def) if (!(attr)) { attr = def; }
#define ADD_METHOD(m) ADD_METHOD_OR_SLOT(m.ml_name, NULL, m.ml_meth, m.ml_flags, NULL, m.ml_doc)
#define ADD_SLOT(name, meth, flags) ADD_METHOD_OR_SLOT(name, NULL, meth, flags, NULL, name)
#define ADD_SLOT_PRIMITIVE(name, meth, flags) ADD_METHOD_OR_SLOT(name, NULL, meth, flags, NULL, name)
#define ADD_SLOT_CONV(name, result_conversion, meth, flags, signature) ADD_METHOD_OR_SLOT(name, result_conversion, meth, flags, signature, name)
#define ADD_METHOD_OR_SLOT(__name__, __res_conv__, __meth__, __flags__, __signature__, __doc__) \
	if (__meth__) { \
            add_method_or_slot(cls, dict, (__name__), (__res_conv__), (__meth__), (__flags__), (__signature__), (__doc__)); \
	}

    Py_ssize_t n;
    Py_ssize_t i;

    // https://docs.python.org/3/c-api/typeobj.html#Py_TPFLAGS_READY
    if ((cls->tp_flags & Py_TPFLAGS_READY) || (cls->tp_flags & Py_TPFLAGS_READYING)) {
        return 0;
    }
    cls->tp_flags = cls->tp_flags | Py_TPFLAGS_READYING;

    /* Types are often just static mem; so register them to be able to rule out invalid accesses.  */
    if(PyTruffle_Trace_Memory()) {
        pytruffle_trace_type(cls);
    }

    /* IMPORTANT: This is a Truffle-specific statement. Since the refcnt for the type is currently 0 and
       we will create several references to this object that will be collected during the execution of
       this method, we need to keep it alive. */
    Py_INCREF(cls);


    PyTypeObject* base;

    /* Initialize tp_base (defaults to BaseObject unless that's us) */
    base = cls->tp_base;
    if (base == NULL && cls != &PyBaseObject_Type) {
        base = cls->tp_base = &PyBaseObject_Type;
        Py_INCREF(base);
    }

    /* Now the only way base can still be NULL is if type is
     * &PyBaseObject_Type.
     */

    /* Initialize the base class */
    if (base != NULL && !(base->tp_flags & Py_TPFLAGS_READY)) {
        if (PyType_Ready(base) < 0) {
        	RETURN_ERROR(cls);
        }
    }

    /* Initialize ob_type if NULL.      This means extensions that want to be
       compilable separately on Windows can call PyType_Ready() instead of
       initializing the ob_type field of their type objects. */
    /* The test for base != NULL is really unnecessary, since base is only
       NULL when type is &PyBaseObject_Type, and we know its ob_type is
       not NULL (it's initialized to &PyType_Type).      But coverity doesn't
       know that. */
    if (Py_TYPE(cls) == NULL && base != NULL) {
        Py_TYPE(cls) = Py_TYPE(base);
    }


    /* Initialize tp_bases */
    PyObject* bases = cls->tp_bases;
    if (bases == NULL) {
        if (base == NULL) {
            bases = PyTuple_New(0);
        } else {
            bases = PyTuple_Pack(1, base);
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
        	ADD_GETSET(cls, getset.name, getset.get, getset.set, getset.doc, getset.closure);
            getset = getsets[++i];
        }
    }

    /* initialize mro */
    cls->tp_mro = UPCALL_CEXT_O(_jls_PyTruffle_Compute_Mro, cls, polyglot_from_string(cls->tp_name, SRC_CS));

    /* Inherit special flags from dominant base */
    if (cls->tp_base != NULL)
        inherit_special(cls, cls->tp_base);

    /* Initialize tp_dict properly */
    bases = cls->tp_mro;
    assert(bases != NULL);
    assert(PyTuple_Check(bases));
    n = PyTuple_GET_SIZE(bases);
    for (i = 1; i < n; i++) {
        PyObject *b = PyTuple_GET_ITEM(bases, i);
        if (PyType_Check(b))
            inherit_slots(cls, (PyTypeObject *)b);
    }

    ADD_IF_MISSING(cls->tp_alloc, PyType_GenericAlloc);
    ADD_IF_MISSING(cls->tp_new, PyType_GenericNew);

    // add special methods defined directly on the type structs
    ADD_SLOT_PRIMITIVE("__dealloc__", cls->tp_dealloc, -1);
    // https://docs.python.org/3/c-api/typeobj.html#c.PyTypeObject.tp_getattr
    // tp_getattr and tp_setattr are deprecated, and should be the same as
    // tp_getattro and tp_setattro

    // NOTE: The slots may be called from managed code, i.e., we need to wrap the functions
    // and convert arguments that should be C primitives.
    ADD_SLOT_CONV("__getattr__", NULL, cls->tp_getattr, -2, JWRAPPER_GETATTR);
    ADD_SLOT_CONV("__setattr__", NULL, cls->tp_setattr, -3, JWRAPPER_SETATTR);
    ADD_SLOT("__repr__", cls->tp_repr, -1);
    ADD_SLOT_PRIMITIVE("__hash__", cls->tp_hash, -1);
    ADD_SLOT("__call__", cls->tp_call, METH_KEYWORDS | METH_VARARGS);
    ADD_SLOT("__str__", cls->tp_str, -1);
    ADD_SLOT("__getattr__", cls->tp_getattro, -2);
    ADD_SLOT_PRIMITIVE("__setattr__", cls->tp_setattro, -3);
    ADD_SLOT_CONV("__clear__", native_int_to_bool, cls->tp_clear, -1, NULL);

    /* IMPORTANT NOTE: If the class already provides 'tp_richcompare' but this is the default
       'object.__truffle_richcompare__' function, then we need to break a recursive cycle since
       the default function dispatches to the individual comparison functions which would in
       this case again invoke 'object.__truffle_richcompare__'. */
    if (cls->tp_richcompare && cls->tp_richcompare != PyBaseObject_Type.tp_richcompare) {
        ADD_SLOT_CONV("__compare__", NULL, cls->tp_richcompare, -3, JWRAPPER_RICHCMP);
        ADD_SLOT_CONV("__lt__", NULL, cls->tp_richcompare, -2, JWRAPPER_LT);
        ADD_SLOT_CONV("__le__", NULL, cls->tp_richcompare, -2, JWRAPPER_LE);
        ADD_SLOT_CONV("__eq__", NULL, cls->tp_richcompare, -2, JWRAPPER_EQ);
        ADD_SLOT_CONV("__ne__", NULL, cls->tp_richcompare, -2, JWRAPPER_NE);
        ADD_SLOT_CONV("__gt__", NULL, cls->tp_richcompare, -2, JWRAPPER_GT);
        ADD_SLOT_CONV("__ge__", NULL, cls->tp_richcompare, -2, JWRAPPER_GE);
    }
    ADD_SLOT("__iter__", cls->tp_iter, -1);
    ADD_SLOT_CONV("__next__", NULL, cls->tp_iternext, -1, JWRAPPER_ITERNEXT);
    ADD_SLOT("__get__", cls->tp_descr_get, -3);
    ADD_SLOT_PRIMITIVE("__set__", cls->tp_descr_set, -3);
    ADD_SLOT_PRIMITIVE("__init__", cls->tp_init, METH_KEYWORDS | METH_VARARGS);
    ADD_SLOT_CONV("__alloc__", NULL, cls->tp_alloc, -2, JWRAPPER_ALLOC);
    ADD_SLOT("__new__", cls->tp_new, METH_KEYWORDS | METH_VARARGS);
    ADD_SLOT_PRIMITIVE("__free__", cls->tp_free, -1);
    ADD_SLOT_PRIMITIVE("__del__", cls->tp_del, -1);
    ADD_SLOT_PRIMITIVE("__finalize__", cls->tp_finalize, -1);

    PyNumberMethods* numbers = cls->tp_as_number;
    if (numbers) {
        ADD_SLOT("__add__", numbers->nb_add, -2);
        ADD_SLOT_CONV("__radd__", NULL, numbers->nb_add, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__sub__", numbers->nb_subtract, -2);
        ADD_SLOT_CONV("__rsub__", NULL, numbers->nb_subtract, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__mul__", numbers->nb_multiply, -2);
        ADD_SLOT_CONV("__rmul__", NULL, numbers->nb_multiply, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__mod__", numbers->nb_remainder, -2);
        ADD_SLOT_CONV("__rmod__", NULL, numbers->nb_remainder, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__divmod__", numbers->nb_divmod, -2);
        ADD_SLOT_CONV("__rdivmod__", NULL, numbers->nb_divmod, -2, JWRAPPER_REVERSE);
        ADD_SLOT_CONV("__pow__", NULL, numbers->nb_power, -3, JWRAPPER_POW);
        ADD_SLOT_CONV("__rpow__", NULL, numbers->nb_power, -3, JWRAPPER_REVERSE_POW);
        ADD_SLOT("__neg__", numbers->nb_negative, -1);
        ADD_SLOT("__pos__", numbers->nb_positive, -1);
        ADD_SLOT("__abs__", numbers->nb_absolute, -1);
        ADD_SLOT_CONV("__bool__", native_int_to_bool, numbers->nb_bool, -1, NULL);
        ADD_SLOT("__invert__", numbers->nb_invert, -1);
        ADD_SLOT("__lshift__", numbers->nb_lshift, -2);
        ADD_SLOT_CONV("__rlshift__", NULL, numbers->nb_lshift, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__rshift__", numbers->nb_rshift, -2);
        ADD_SLOT_CONV("__rrshift__", NULL, numbers->nb_rshift, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__and__", numbers->nb_and, -2);
        ADD_SLOT_CONV("__rand__", NULL, numbers->nb_and, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__xor__", numbers->nb_xor, -2);
        ADD_SLOT_CONV("__rxor__", NULL, numbers->nb_xor, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__or__", numbers->nb_or, -2);
        ADD_SLOT_CONV("__ror__", NULL, numbers->nb_or, -2, JWRAPPER_REVERSE);
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
        ADD_SLOT_CONV("__rfloordiv__", NULL, numbers->nb_floor_divide, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__truediv__", numbers->nb_true_divide, -2);
        ADD_SLOT_CONV("__rtruediv__", NULL, numbers->nb_true_divide, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__ifloordiv__", numbers->nb_inplace_floor_divide, -2);
        ADD_SLOT("__itruediv__", numbers->nb_inplace_true_divide, -2);
        ADD_SLOT("__index__", numbers->nb_index, -1);
        ADD_SLOT("__matmul__", numbers->nb_matrix_multiply, -2);
        ADD_SLOT_CONV("__rmatmul__", NULL, numbers->nb_matrix_multiply, -2, JWRAPPER_REVERSE);
        ADD_SLOT("__imatmul__", numbers->nb_inplace_matrix_multiply, -2);
    }

    PySequenceMethods* sequences = cls->tp_as_sequence;
    if (sequences) {
        ADD_SLOT_PRIMITIVE("__len__", sequences->sq_length, -1);
        ADD_SLOT("__add__", sequences->sq_concat, -2);
        ADD_SLOT_CONV("__mul__", NULL, sequences->sq_repeat, -2, JWRAPPER_SSIZE_ARG);
        ADD_SLOT_CONV("__getitem__", NULL, sequences->sq_item, -2, JWRAPPER_SSIZE_ARG);
        ADD_SLOT_CONV("__setitem__", NULL, sequences->sq_ass_item, -3, JWRAPPER_SSIZE_OBJ_ARG);
        ADD_SLOT_PRIMITIVE("__contains__", sequences->sq_contains, -2);
        ADD_SLOT("__iadd__", sequences->sq_inplace_concat, -2);
        ADD_SLOT_CONV("__imul__", NULL, sequences->sq_inplace_repeat, -2, JWRAPPER_SSIZE_ARG);
    }

    PyMappingMethods* mappings = cls->tp_as_mapping;
    if (mappings) {
        ADD_SLOT_PRIMITIVE("__len__", mappings->mp_length, -1);
        ADD_SLOT("__getitem__", mappings->mp_subscript, -2);
        ADD_SLOT_PRIMITIVE("__setitem__", mappings->mp_ass_subscript, -3);
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
    /*
    UPCALL_CEXT_O(_jls_PyTruffle_Type_Slots, native_to_java((PyObject*)cls), native_to_java(cls->tp_dict));
    */
    PyGetSetDef** inherited_getset = (PyGetSetDef**) UPCALL_CEXT_PTR(_jls_PyTruffle_Get_Inherited_Native_Slots, native_to_java((PyObject*)cls), polyglot_from_string("getsets", SRC_CS));
    PyMemberDef** inherited_members = (PyMemberDef**) UPCALL_CEXT_PTR(_jls_PyTruffle_Get_Inherited_Native_Slots, native_to_java((PyObject*)cls), polyglot_from_string("members", SRC_CS));
    uint64_t n_getsets = polyglot_get_array_size(inherited_getset);
    uint64_t n_members = polyglot_get_array_size(inherited_members);
  	PyTruffle_Type_AddSlots(cls, inherited_getset, n_getsets, inherited_members, n_members);

    /* Initialize this classes' tp_subclasses dict. This is necessary because our managed classes won't do. */
    cls->tp_subclasses = PyDict_New();

    /* if the type dictionary doesn't contain a __doc__, set it from
       the tp_doc slot.
     */
    PyObject* doc_id = (PyObject *)polyglot_from_string("__doc__", SRC_CS);
    if (PyDict_GetItem(cls->tp_dict, doc_id) == NULL) {
        if (cls->tp_doc != NULL) {
            const char *old_doc = _PyType_DocWithoutSignature(cls->tp_name, cls->tp_doc);
            PyObject *doc = PyUnicode_FromString(old_doc);
            if (doc == NULL) {
                RETURN_ERROR(cls);
            }
            if (PyDict_SetItem(cls->tp_dict, doc_id, doc) < 0) {
                Py_DECREF(doc);
                RETURN_ERROR(cls);
            }
            Py_DECREF(doc);
        } else if (PyDict_SetItem(cls->tp_dict, doc_id, Py_None) < 0) {
            RETURN_ERROR(cls);
        }
    }

    /* Link into each base class's list of subclasses */
    bases = cls->tp_bases;
    n = PyTuple_GET_SIZE(bases);
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

    // it may be that the type was used uninitialized
    UPCALL_CEXT_VOID(_jls_PyTruffle_Type_Modified, cls, polyglot_from_string(cls->tp_name, SRC_CS), Py_NoValue);

	// Truffle-specific decref (for reason, see first call to Py_INCREF in this function)
	Py_DECREF(cls);

    return 0;

#undef ADD_IF_MISSING
#undef ADD_METHOD
#undef ADD_SLOT
#undef ADD_METHOD_OR_SLOT
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

/* Add get-set descriptors for slots provided in 'getsets' and 'members'. */
static void PyTruffle_Type_AddSlots(PyTypeObject* cls, PyGetSetDef** getsets, uint64_t n_getsets, PyMemberDef** members, uint64_t n_members) {
	for (uint64_t j = 0; j < n_getsets; j++) {
		PyGetSetDef* getsets_sub = getsets[j];
		if (getsets_sub) {
			int i = 0;
			PyGetSetDef getset = getsets_sub[i];
			while (getset.name != NULL) {
				ADD_GETSET(cls, getset.name, getset.get, getset.set, getset.doc, getset.closure);
				getset = getsets_sub[++i];
			}
		}
	}
	for (uint64_t j = 0; j < n_getsets; j++) {
		PyMemberDef* members_sub = members[j];
		if (members_sub) {
			int i = 0;
			PyMemberDef member = members_sub[i];
			PyObject* dict = cls->tp_dict;
			while (member.name != NULL) {
				ADD_MEMBER(cls, dict, polyglot_from_string(member.name, SRC_CS), member.type, member.offset, member.flags, member.doc);
				member = members_sub[++i];
			}
		}
	}
}

unsigned long PyType_GetFlags(struct _typeobject *type) {
    return type->tp_flags;
}

// taken from CPython "Objects/typeobject.c"
static int
extra_ivars(PyTypeObject *type, PyTypeObject *base)
{
    size_t t_size = type->tp_basicsize;
    size_t b_size = base->tp_basicsize;

    assert(t_size >= b_size); /* Else type smaller than base! */
    if (type->tp_itemsize || base->tp_itemsize) {
        /* If itemsize is involved, stricter rules */
        return t_size != b_size ||
            type->tp_itemsize != base->tp_itemsize;
    }
    if (type->tp_weaklistoffset && base->tp_weaklistoffset == 0 &&
        type->tp_weaklistoffset + sizeof(PyObject *) == t_size &&
        type->tp_flags & Py_TPFLAGS_HEAPTYPE)
        t_size -= sizeof(PyObject *);
    if (type->tp_dictoffset && base->tp_dictoffset == 0 &&
        type->tp_dictoffset + sizeof(PyObject *) == t_size &&
        type->tp_flags & Py_TPFLAGS_HEAPTYPE)
        t_size -= sizeof(PyObject *);

    return t_size != b_size;
}

// taken from CPython "Objects/typeobject.c"
static PyTypeObject *
solid_base(PyTypeObject *type)
{
    PyTypeObject *base;

    if (type->tp_base)
        base = solid_base(type->tp_base);
    else
        base = &PyBaseObject_Type;
    if (extra_ivars(type, base))
        return type;
    else
        return base;
}

// taken from CPython "Objects/typeobject.c"
/* Calculate the best base amongst multiple base classes.
   This is the first one that's on the path to the "solid base". */
static PyTypeObject *
best_base(PyObject *bases)
{
    Py_ssize_t i, n;
    PyTypeObject *base, *winner, *candidate, *base_i;
    PyObject *base_proto;

    assert(PyTuple_Check(bases));
    n = PyTuple_GET_SIZE(bases);
    assert(n > 0);
    base = NULL;
    winner = NULL;
    for (i = 0; i < n; i++) {
        base_proto = PyTuple_GET_ITEM(bases, i);
        if (!PyType_Check(base_proto)) {
            PyErr_SetString(
                PyExc_TypeError,
                "bases must be types");
            return NULL;
        }
        base_i = (PyTypeObject *)base_proto;
        if (base_i->tp_dict == NULL) {
            if (PyType_Ready(base_i) < 0)
                return NULL;
        }
        if (!PyType_HasFeature(base_i, Py_TPFLAGS_BASETYPE)) {
            PyErr_Format(PyExc_TypeError,
                         "type '%.100s' is not an acceptable base type",
                         base_i->tp_name);
            return NULL;
        }
        candidate = solid_base(base_i);
        if (winner == NULL) {
            winner = candidate;
            base = base_i;
        }
        else if (PyType_IsSubtype(winner, candidate))
            ;
        else if (PyType_IsSubtype(candidate, winner)) {
            winner = candidate;
            base = base_i;
        }
        else {
            PyErr_SetString(
                PyExc_TypeError,
                "multiple bases have "
                "instance lay-out conflict");
            return NULL;
        }
    }
    assert (base != NULL);

    return base;
}
// taken from CPython "Objects/typeobject.c"
static const short slotoffsets[] = {
    -1, /* invalid slot */
#include "typeslots.inc"
};

// taken from CPython "Objects/typeobject.c"
PyObject *
PyType_FromSpecWithBases(PyType_Spec *spec, PyObject *bases)
{
    PyHeapTypeObject *res;
    PyMemberDef *memb;
    PyObject *modname;
    PyTypeObject *type, *base;

    PyType_Slot *slot;
    Py_ssize_t nmembers;
    char *s, *res_start;

    nmembers = 0;
    for (slot = spec->slots; slot->slot; slot++) {
        if (slot->slot == Py_tp_members) {
            nmembers = 0;
            for (memb = slot->pfunc; memb->name != NULL; memb++) {
                nmembers++;
            }
        }
    }

    res = (PyHeapTypeObject*)PyType_GenericAlloc(&PyType_Type, nmembers);
    if (res == NULL)
        return NULL;
    res_start = (char*)res;

    if (spec->name == NULL) {
        PyErr_SetString(PyExc_SystemError,
                        "Type spec does not define the name field.");
        goto fail;
    }

    /* Set the type name and qualname */
    s = strrchr(spec->name, '.');
    if (s == NULL)
        s = (char*)spec->name;
    else
        s++;

    type = &res->ht_type;
    /* The flags must be initialized early, before the GC traverses us */
    type->tp_flags = spec->flags | Py_TPFLAGS_HEAPTYPE;
    res->ht_name = PyUnicode_FromString(s);
    if (!res->ht_name)
        goto fail;
    res->ht_qualname = res->ht_name;
    Py_INCREF(res->ht_qualname);
    type->tp_name = spec->name;

    /* Adjust for empty tuple bases */
    if (!bases) {
        base = &PyBaseObject_Type;
        /* See whether Py_tp_base(s) was specified */
        for (slot = spec->slots; slot->slot; slot++) {
            if (slot->slot == Py_tp_base)
                base = slot->pfunc;
            else if (slot->slot == Py_tp_bases) {
                bases = slot->pfunc;
                Py_INCREF(bases);
            }
        }
        if (!bases)
            bases = PyTuple_Pack(1, base);
        if (!bases)
            goto fail;
    }
    else
        Py_INCREF(bases);

    /* Calculate best base, and check that all bases are type objects */
    base = best_base(bases);
    if (base == NULL) {
        goto fail;
    }
    if (!PyType_HasFeature(base, Py_TPFLAGS_BASETYPE)) {
        PyErr_Format(PyExc_TypeError,
                     "type '%.100s' is not an acceptable base type",
                     base->tp_name);
        goto fail;
    }

    /* Initialize essential fields */
    type->tp_as_async = &res->as_async;
    type->tp_as_number = &res->as_number;
    type->tp_as_sequence = &res->as_sequence;
    type->tp_as_mapping = &res->as_mapping;
    type->tp_as_buffer = &res->as_buffer;
    /* Set tp_base and tp_bases */
    type->tp_bases = bases;
    bases = NULL;
    Py_INCREF(base);
    type->tp_base = base;

    type->tp_basicsize = spec->basicsize;
    type->tp_itemsize = spec->itemsize;

    for (slot = spec->slots; slot->slot; slot++) {
        if (slot->slot < 0
            || (size_t)slot->slot >= Py_ARRAY_LENGTH(slotoffsets)) {
            PyErr_SetString(PyExc_RuntimeError, "invalid slot offset");
            goto fail;
        }
        else if (slot->slot == Py_tp_base || slot->slot == Py_tp_bases) {
            /* Processed above */
            continue;
        }
        else if (slot->slot == Py_tp_doc) {
            /* For the docstring slot, which usually points to a static string
               literal, we need to make a copy */
            const char *old_doc = _PyType_DocWithoutSignature(type->tp_name, slot->pfunc);
            size_t len = strlen(old_doc)+1;
            char *tp_doc = PyObject_MALLOC(len);
            if (tp_doc == NULL) {
                type->tp_doc = NULL;
                PyErr_NoMemory();
                goto fail;
            }
            memcpy(tp_doc, old_doc, len);
            type->tp_doc = tp_doc;
        }
        else if (slot->slot == Py_tp_members) {
            /* Move the slots to the heap type itself */
            size_t len = Py_TYPE(type)->tp_itemsize * nmembers;
            memcpy(PyHeapType_GET_MEMBERS(res), slot->pfunc, len);
            type->tp_members = PyHeapType_GET_MEMBERS(res);
        }
        else {
            /* Copy other slots directly */
            *(void**)(res_start + slotoffsets[slot->slot]) = slot->pfunc;
        }
    }
    if (type->tp_dealloc == NULL) {
        /* It's a heap type, so needs the heap types' dealloc.
           subtype_dealloc will call the base type's tp_dealloc, if
           necessary. */
    	/* TODO(fa): patched out */
        /* type->tp_dealloc = subtype_dealloc; */
    }

    if (PyType_Ready(type) < 0)
        goto fail;

    if (type->tp_dictoffset) {
        res->ht_cached_keys = _PyDict_NewKeysForClass();
    }

    /* Set type.__module__ */
    s = strrchr(spec->name, '.');
    if (s != NULL) {
        int err;
        modname = PyUnicode_FromStringAndSize(
                spec->name, (Py_ssize_t)(s - spec->name));
        if (modname == NULL) {
            goto fail;
        }
        err = PyDict_SetItemString(type->tp_dict, "__module__", modname);
        Py_DECREF(modname);
        if (err != 0)
            goto fail;
    } else {
        if (PyErr_WarnFormat(PyExc_DeprecationWarning, 1,
                "builtin type %.200s has no __module__ attribute",
                spec->name))
            goto fail;
    }

    return (PyObject*)res;

 fail:
    Py_DECREF(res);
    return NULL;
}

// taken from CPython "Objects/typeobject.c"
PyObject *
PyType_FromSpec(PyType_Spec *spec)
{
    return PyType_FromSpecWithBases(spec, NULL);
}
