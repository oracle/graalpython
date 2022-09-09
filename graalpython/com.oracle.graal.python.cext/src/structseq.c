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

#define REAL_SIZE_TP(tp) PyLong_AsSsize_t(PyDict_GetItem((tp)->tp_dict, polyglot_from_string("n_fields", SRC_CS)))
#define REAL_SIZE(op) REAL_SIZE_TP(Py_TYPE(op))

char *PyStructSequence_UnnamedField = "unnamed field";

static void
structseq_dealloc(PyStructSequence *obj)
{
	obj = native_pointer_to_java(obj);
    Py_ssize_t i, size;

    size = REAL_SIZE(obj);
    for (i = 0; i < size; ++i) {
        Py_XDECREF(obj->ob_item[i]);
    }
    PyObject_GC_Del(obj);
}

typedef PyObject *(*structseq_new_fun_t)(PyTypeObject *);
UPCALL_TYPED_ID(PyStructSequence_New, structseq_new_fun_t);
PyObject* PyStructSequence_New(PyTypeObject* o) {
    return _jls_PyStructSequence_New(native_type_to_java(o));
}

static Py_ssize_t
count_members(PyStructSequence_Desc *desc, Py_ssize_t *n_unnamed_members) {
    Py_ssize_t i;

    *n_unnamed_members = 0;
    for (i = 0; desc->fields[i].name != NULL; ++i) {
        if (desc->fields[i].name == PyStructSequence_UnnamedField) {
            (*n_unnamed_members)++;
        }
    }
    return i;
}

typedef int (*structseq_init_fun_t)(void *, void *, void *, int);
UPCALL_TYPED_ID(PyStructSequence_InitType2, structseq_init_fun_t);
int PyStructSequence_InitType2(PyTypeObject *type, PyStructSequence_Desc *desc) {
    Py_ssize_t n_members, n_unnamed_members, n_named_members;
    Py_ssize_t i;

    memset(type, 0, sizeof(PyTypeObject));


    // copy generic fields (CPython mem-copies a template)
    type->tp_name = desc->name;
    type->tp_basicsize = sizeof(PyStructSequence) - sizeof(PyObject *);
    type->tp_itemsize = sizeof(PyObject *);
    type->tp_flags = Py_TPFLAGS_DEFAULT;
    type->tp_members = NULL;
    type->tp_doc = desc->doc;
    type->tp_base = &PyTuple_Type;
    type->tp_dealloc = (destructor)structseq_dealloc;

    // now initialize the type
    if (PyType_Ready(type) < 0)
        return -1;
    Py_INCREF(type);

    n_members = count_members(desc, &n_unnamed_members);
    n_named_members = n_members - n_unnamed_members;
    // put field names and doc strings into two lists
    void** field_names = (void **) truffle_managed_malloc(n_named_members * sizeof(void *));
    void** field_docs = (void **) truffle_managed_malloc(n_named_members * sizeof(void *));
    PyStructSequence_Field* fields = desc->fields;
    int j = 0;
    for (i = 0; i < n_members; i++) {
        if (fields[i].name != PyStructSequence_UnnamedField) {
            field_names[j] = polyglot_from_string(fields[i].name, SRC_CS);
            field_docs[j] = polyglot_from_string(fields[i].doc, SRC_CS);
            j++;
        }
    }

    // this initializes the type dict (adds attributes)
    return _jls_PyStructSequence_InitType2(
            native_type_to_java(type),
            /* TODO(fa): use polyglot_from_VoidPtr_array once this is visible */
            polyglot_from_PyObjectPtr_array((PyObjectPtr *) field_names, (uint64_t) n_members),
            polyglot_from_PyObjectPtr_array((PyObjectPtr *) field_docs, (uint64_t) n_members),
            desc->n_in_sequence
    );
}

typedef PyTypeObject *(*structseq_newtype_fun_t)(void *, void *, void *, void *, int);
UPCALL_TYPED_ID(PyStructSequence_NewType, structseq_newtype_fun_t);
PyTypeObject* PyStructSequence_NewType(PyStructSequence_Desc *desc) {
    Py_ssize_t n_members, n_unnamed_members, n_named_members;
    Py_ssize_t i;

    n_members = count_members(desc, &n_unnamed_members);
    n_named_members = n_members - n_unnamed_members;
    // put field names and doc strings into two lists
    void** field_names = (void **) truffle_managed_malloc(n_named_members * sizeof(void *));
    void** field_docs = (void **) truffle_managed_malloc(n_named_members * sizeof(void *));
    PyStructSequence_Field* fields = desc->fields;
    int j = 0;
    for (i = 0; i < n_members; i++) {
        if (fields[i].name != PyStructSequence_UnnamedField) {
            field_names[j] = polyglot_from_string(fields[i].name, SRC_CS);
            field_docs[j] = polyglot_from_string(fields[i].doc, SRC_CS);
            j++;
        }
    }

    // we create the new type managed
    return _jls_PyStructSequence_NewType(
            polyglot_from_string(desc->name, SRC_CS),
            polyglot_from_string(desc->doc, SRC_CS),
            /* TODO(fa): use polyglot_from_VoidPtr_array once this is visible */
            polyglot_from_PyObjectPtr_array((PyObjectPtr *) field_names, (uint64_t) n_members),
            polyglot_from_PyObjectPtr_array((PyObjectPtr *) field_docs, (uint64_t) n_members),
            desc->n_in_sequence
    );
}

// taken from CPython "Objects/structseq.c"
void PyStructSequence_InitType(PyTypeObject *type, PyStructSequence_Desc *desc) {
    (void)PyStructSequence_InitType2(type, desc);
}

// taken from CPython "Objects/structseq.c"
void PyStructSequence_SetItem(PyObject* op, Py_ssize_t i, PyObject* v) {
    PyStructSequence_SET_ITEM(op, i, v);
}

// taken from CPython "Objects/structseq.c"
PyObject* PyStructSequence_GetItem(PyObject* op, Py_ssize_t i) {
	return PyStructSequence_GET_ITEM(op, i);
}

