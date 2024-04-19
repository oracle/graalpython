/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

static int
_list_clear(PyListObject *a)
{
    int64_t i;
    PyObject **item;

    /* Because XDECREF can recursively invoke operations on
       this list, we make it empty first. */
    i = GraalPyTruffleList_ClearManagedOrGetItems(a, &item);
    if (i > 0) {
        assert(item != NULL);
        while (--i >= 0) {
            Py_XDECREF(item[i]);
        }
        /* CPython calls 'PyMem_Free(item)' here but in our case, this will be
           done by the NativeStorageReference. Since we already set the length
           to zero, the items won't be decref'd again. */
    }
    /* Never fails; the return value can be ignored.
       Note that there is no guarantee that the list is actually empty
       at this point, because XDECREF may have populated it again! */
    return 0;
}

static int
list_traverse(PyListObject *o, visitproc visit, void *arg)
{
    int64_t size, i;
    PyObject **ob_item;

    size = GraalPyTruffleList_TraverseManagedOrGetItems(o, &ob_item, visit, arg);
    for (i = size; --i >= 0; )
        Py_VISIT(ob_item[i]);
    return 0;
}

PyTypeObject PyList_Type = {
    PyVarObject_HEAD_INIT(&PyType_Type, 0)
    "list",
    sizeof(PyListObject),
    0,
    0,                                          /* tp_dealloc */ // GraalPy change: nulled
    0,                                          /* tp_vectorcall_offset */
    0,                                          /* tp_getattr */
    0,                                          /* tp_setattr */
    0,                                          /* tp_as_async */
    0,                                          /* tp_repr */ // GraalPy change: nulled
    0,                                          /* tp_as_number */
    0,                                          /* tp_as_sequence */ // GraalPy change: nulled
    0,                                          /* tp_as_mapping */ // GraalPy change: nulled
    PyObject_HashNotImplemented,                /* tp_hash */
    0,                                          /* tp_call */
    0,                                          /* tp_str */
    0,                                          /* tp_getattro */ // GraalPy change: nulled
    0,                                          /* tp_setattro */
    0,                                          /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC |
        Py_TPFLAGS_BASETYPE | Py_TPFLAGS_LIST_SUBCLASS |
        _Py_TPFLAGS_MATCH_SELF | Py_TPFLAGS_SEQUENCE,  /* tp_flags */
    0,                                          /* tp_doc */ // GraalPy change: nulled
    (traverseproc)list_traverse,                /* tp_traverse */
    (inquiry)_list_clear,                       /* tp_clear */
    0,                                          /* tp_richcompare */ // GraalPy change: nulled
    0,                                          /* tp_weaklistoffset */
    0,                                          /* tp_iter */ // GraalPy change: nulled
    0,                                          /* tp_iternext */
    0,                                          /* tp_methods */ // GraalPy change: nulled
    0,                                          /* tp_members */
    0,                                          /* tp_getset */
    0,                                          /* tp_base */
    0,                                          /* tp_dict */
    0,                                          /* tp_descr_get */
    0,                                          /* tp_descr_set */
    0,                                          /* tp_dictoffset */
    0,                                          /* tp_init */ // GraalPy change: nulled
    PyType_GenericAlloc,                        /* tp_alloc */
    PyType_GenericNew,                          /* tp_new */
    GraalPyObject_GC_Del,                       /* tp_free */
    .tp_vectorcall = 0, // GraalPy change: nulled
};

// alias for internal function, currently used in PyO3
void _PyList_SET_ITEM(PyObject* a, Py_ssize_t b, PyObject* c) {
    return PyList_SET_ITEM(a, b, c);
}

static inline int
valid_index(Py_ssize_t i, Py_ssize_t limit)
{
    /* The cast to size_t lets us use just a single comparison
       to check whether i is in the range: 0 <= i < limit.

       See:  Section 14.2 "Bounds Checking" in the Agner Fog
       optimization manual found at:
       https://www.agner.org/optimize/optimizing_cpp.pdf
    */
    return (size_t) i < (size_t) limit;
}

int
PyList_SetItem(PyObject *op, Py_ssize_t i,
               PyObject *newitem)
{
    PyObject **p;
    if (!PyList_Check(op)) {
        Py_XDECREF(newitem);
        PyErr_BadInternalCall();
        return -1;
    }
    if (!valid_index(i, Py_SIZE(op))) {
        Py_XDECREF(newitem);
        PyErr_SetString(PyExc_IndexError,
                        "list assignment index out of range");
        return -1;
    }
    // GraalPy change: avoid direct struct access
    p = PyTruffleList_GetItems(op) + i;
    Py_XSETREF(*p, newitem);
    return 0;
}

// GraalPy-additions
PyObject **
PyTruffleList_GetItems(PyObject *op)
{
    return PyListObject_ob_item(op);
}
