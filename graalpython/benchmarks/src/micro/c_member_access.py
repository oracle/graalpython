# Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

code = """
#include <Python.h>
#include "structmember.h"

typedef struct {
    PyObject_HEAD
    int number;
} ObjectWithMember;

PyObject* ObjectWithMember_new(PyTypeObject *type, PyObject *args, PyObject *kwds) {
    ObjectWithMember* self = (ObjectWithMember *)type->tp_alloc(type, 0);
    if (self != NULL) {
        self->number = 0;
    }
    return (PyObject *)self;
}

int ObjectWithMember_init(ObjectWithMember* self, PyObject* args, PyObject* kwds) {
    if (!PyArg_ParseTuple(args, "i", &self->number)) {
        return -1;
    }
    return 0;
}

static PyMemberDef ObjectWithMember_members[] = {
    {"number", T_INT, offsetof(ObjectWithMember, number), 0, ""},
    {NULL}
};

static PyTypeObject ObjectWithMemberType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "module.ObjectWithMember", /* tp_name */
    sizeof(ObjectWithMember),  /* tp_basicsize */
    0,                         /* tp_itemsize */
    0,                         /* tp_dealloc */
    0,                         /* tp_vectorcall_offset */
    0,                         /* tp_getattr */
    0,                         /* tp_setattr */
    0,                         /* tp_reserved */
    0,                         /* tp_repr */
    0,                         /* tp_as_number */
    0,                         /* tp_as_sequence */
    0,                         /* tp_as_mapping */
    0,                         /* tp_hash  */
    0,                         /* tp_call */
    0,                         /* tp_str */
    0,                         /* tp_getattro */
    0,                         /* tp_setattro */
    0,                         /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT |
        Py_TPFLAGS_BASETYPE,   /* tp_flags */
    0,                        /* tp_doc */
    0,                         /* tp_traverse */
    0,                         /* tp_clear */
    0,                         /* tp_richcompare */
    0,                         /* tp_weaklistoffset */
    0,                         /* tp_iter */
    0,                         /* tp_iternext */
    0,                         /* tp_methods */
    ObjectWithMember_members,  /* tp_members */
    0,                         /* tp_getset */
    0,                         /* tp_base */
    0,                         /* tp_dict */
    0,                         /* tp_descr_get */
    0,                         /* tp_descr_set */
    0,                         /* tp_dictoffset */
    (initproc)ObjectWithMember_init,      /* tp_init */
    0,                         /* tp_alloc */
    ObjectWithMember_new,      /* tp_new */
};

static PyModuleDef module = {
    PyModuleDef_HEAD_INIT,
    "module",
    "",
    -1,
    NULL, NULL, NULL, NULL, NULL
};

PyMODINIT_FUNC
PyInit_c_member_access_module(void) {
    if (PyType_Ready(&ObjectWithMemberType) < 0) {
        return NULL;
    }

    PyObject* m = PyModule_Create(&module);
    if (m == NULL) {
        return NULL;
    }

    Py_INCREF(&ObjectWithMemberType);
    PyModule_AddObject(m, "ObjectWithMember", (PyObject *)&ObjectWithMemberType);
    return m;
}
"""


ccompile("c_member_access_module", code)
import c_member_access_module


def do_stuff(foo):
    for i in range(50000):
        local_a = foo.number + 1
        foo.number = local_a % 5

    return foo.number


def measure(num):
    for i in range(num):
        result = do_stuff(c_member_access_module.ObjectWithMember(42))

    print(result)


def __benchmark__(num=50000):
    measure(num)
