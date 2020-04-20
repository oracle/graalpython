# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

static PyTypeObject FloatSubclass;

static PyObject* new_fp(double val) {
    PyFloatObject* fp = PyObject_New(PyFloatObject, &FloatSubclass);
    fp->ob_fval = val;
    return (PyObject*)fp;
}

static PyObject* fp_add(PyObject* l, PyObject* r) {
    if (PyFloat_Check(l)) {
        if (PyFloat_Check(r)) {
            return new_fp(PyFloat_AS_DOUBLE(l) + PyFloat_AS_DOUBLE(r));
        } else if (PyLong_Check(r)) {
            return new_fp(PyFloat_AS_DOUBLE(l) + PyLong_AsLong(r));
        }
    } else if (PyLong_Check(l)) {
        if (PyFloat_Check(r)) {
            return new_fp(PyLong_AsLong(l) + PyFloat_AS_DOUBLE(r));
        } else if (PyLong_Check(r)) {
            return new_fp(PyLong_AsLong(l) + PyLong_AsLong(r));
        }
    }
    return Py_NotImplemented;
}

static PyObject* fp_mul(PyObject* l, PyObject* r) {
    if (PyFloat_Check(l)) {
        if (PyFloat_Check(r)) {
            return new_fp(PyFloat_AS_DOUBLE(l) * PyFloat_AS_DOUBLE(r));
        } else if (PyLong_Check(r)) {
            return new_fp(PyFloat_AS_DOUBLE(l) * PyLong_AsLong(r));
        }
    }
    return Py_NotImplemented;
}

static PyObject* fp_div(PyObject* l, PyObject* r) {
    if (PyFloat_Check(l)) {
        if (PyFloat_Check(r)) {
            return new_fp(PyFloat_AS_DOUBLE(l) / PyFloat_AS_DOUBLE(r));
        } else if (PyLong_Check(r)) {
            return new_fp(PyFloat_AS_DOUBLE(l) / PyLong_AsLong(r));
        }
    }
    return Py_NotImplemented;
}

static PyNumberMethods FloatSubclassMethods = {
    fp_add, // binaryfunc nb_add;
    0, // binaryfunc nb_subtract;
    fp_mul, // binaryfunc nb_multiply;
    0, // binaryfunc nb_remainder;
    0, // binaryfunc nb_divmod;
    0, // ternaryfunc nb_power;
    0, // unaryfunc nb_negative;
    0, // unaryfunc nb_positive;
    0, // unaryfunc nb_absolute;
    0, // inquiry nb_bool;
    0, // unaryfunc nb_invert;
    0, // binaryfunc nb_lshift;
    0, // binaryfunc nb_rshift;
    0, // binaryfunc nb_and;
    0, // binaryfunc nb_xor;
    0, // binaryfunc nb_or;
    0, // unaryfunc nb_int;
    0, // void *nb_reserved;
    0, // unaryfunc nb_float;

    0, // binaryfunc nb_inplace_add;
    0, // binaryfunc nb_inplace_subtract;
    0, // binaryfunc nb_inplace_multiply;
    0, // binaryfunc nb_inplace_remainder;
    0, // ternaryfunc nb_inplace_power;
    0, // binaryfunc nb_inplace_lshift;
    0, // binaryfunc nb_inplace_rshift;
    0, // binaryfunc nb_inplace_and;
    0, // binaryfunc nb_inplace_xor;
    0, // binaryfunc nb_inplace_or;

    0, // binaryfunc nb_floor_divide;
    fp_div, // binaryfunc nb_true_divide;
    0, // binaryfunc nb_inplace_floor_divide;
    0, // binaryfunc nb_inplace_true_divide;

    0, // unaryfunc nb_index;

    0, // binaryfunc nb_matrix_multiply;
    0  // binaryfunc nb_inplace_matrix_multiply;
};

static PyTypeObject FloatSubclass = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "FloatSubclass", /* tp_name */
    sizeof(PyFloatObject),  /* tp_basicsize */
    0,                         /* tp_itemsize */
    0,                         /* tp_dealloc */
    0,                         /* tp_vectorcall_offset */
    0,                         /* tp_getattr */
    0,                         /* tp_setattr */
    0,                         /* tp_reserved */
    0,                         /* tp_repr */
    &FloatSubclassMethods,     /* tp_as_number */
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
    0,  /* tp_members */
    0,                         /* tp_getset */
    &PyFloat_Type,                         /* tp_base */
    0,                         /* tp_dict */
    0,                         /* tp_descr_get */
    0,                         /* tp_descr_set */
    0,                         /* tp_dictoffset */
    0,      /* tp_init */
    0,                         /* tp_alloc */
    0,      /* tp_new */
};

static PyModuleDef module = {
    PyModuleDef_HEAD_INIT,
    "module",
    "",
    -1,
    NULL, NULL, NULL, NULL, NULL
};

PyMODINIT_FUNC
PyInit_c_arith_binop_module(void) {
    PyType_Ready(&FloatSubclass);
    PyObject* m = PyModule_Create(&module);
    PyModule_AddObject(m, "FloatSubclass", (PyObject *)&FloatSubclass);
    return m;
}
"""


ccompile("c_arith_binop_module", code)
from c_arith_binop_module import FloatSubclass


def docompute(num):
    for i in range(num):
        sum_ = FloatSubclass(0.0)
        j = FloatSubclass(0)
        while j < num:
            sum_ += ((i + j) * (i + j + 1) + i + 1)
            j += 1

    return sum_, type(sum_)


def measure(num):
    for run in range(num):
        sum_ = docompute(num * 2)
    print("sum", sum_)


def __benchmark__(num=5):
    measure(num)
