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

/* StructSequences a.k.a. 'namedtuple' */
UPCALL_ID(PyStructSequence_New);
PyObject* PyStructSequence_New(PyTypeObject* o) {
    return UPCALL_CEXT_O(_jls_PyStructSequence_New, native_type_to_java(o));
}

UPCALL_ID(PyStructSequence_InitType2);
int PyStructSequence_InitType2(PyTypeObject *type, PyStructSequence_Desc *desc) {
    Py_ssize_t n_members = desc->n_in_sequence;
    Py_ssize_t i;

    memset(type, 0, sizeof(PyTypeObject));

    // put field names and doc strings into two tuples
    PyObject* field_names = PyTuple_New(n_members);
    PyObject* field_docs = PyTuple_New(n_members);
    PyStructSequence_Field* fields = desc->fields;
    for (i = 0; i < n_members; i++) {
    	PyTuple_SetItem(field_names, i, polyglot_from_string(fields[i].name, SRC_CS));
    	PyTuple_SetItem(field_docs, i, polyglot_from_string(fields[i].doc, SRC_CS));
    }

    // we create the new type managed
    PyTypeObject* newType = (PyTypeObject*) UPCALL_CEXT_O(_jls_PyStructSequence_InitType2,
    		polyglot_from_string(desc->name, SRC_CS),
			polyglot_from_string(desc->doc, SRC_CS),
			native_to_java(field_names),
			native_to_java(field_docs));

    // copy generic fields (CPython mem-copies a template)
    type->tp_basicsize = sizeof(PyStructSequence) - sizeof(PyObject *);
    type->tp_itemsize = sizeof(PyObject *);
    type->tp_repr = newType->tp_repr;
    type->tp_flags = Py_TPFLAGS_DEFAULT;
    type->tp_members = NULL;
    type->tp_new = newType->tp_new;
    type->tp_base = &PyTuple_Type;
    type->tp_alloc = newType->tp_alloc;

    // now copy specific fields
    type->tp_name = newType->tp_name;
    type->tp_doc = newType->tp_doc;
    type->tp_dict = newType->tp_dict;

    // now initialize the type
    if (PyType_Ready(type) < 0)
        return -1;
    Py_INCREF(type);

    return 0;
}

PyTypeObject* PyStructSequence_NewType(PyStructSequence_Desc *desc) {
    Py_ssize_t n_members = desc->n_in_sequence;
    Py_ssize_t i;

    // put field names and doc strings into two tuples
    PyObject* field_names = PyTuple_New(n_members);
    PyObject* field_docs = PyTuple_New(n_members);
    PyStructSequence_Field* fields = desc->fields;
    for (i = 0; i < n_members; i++) {
    	PyTuple_SetItem(field_names, i, polyglot_from_string(fields[i].name, SRC_CS));
    	PyTuple_SetItem(field_docs, i, polyglot_from_string(fields[i].doc, SRC_CS));
    }

    // we create the new type managed
    PyTypeObject* newType = (PyTypeObject*) UPCALL_CEXT_O(_jls_PyStructSequence_InitType2,
    		polyglot_from_string(desc->name, SRC_CS),
			polyglot_from_string(desc->doc, SRC_CS),
			native_to_java(field_names),
			native_to_java(field_docs));
    return newType;
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

