# Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "Python.h"

PyObject* simple_method(PyObject* mod, PyObject* arg) {
    return PyFloat_FromDouble(PyFloat_AsDouble(arg));
}

static PyMethodDef MyModuleMethods[] = {
    {"as_double", simple_method, METH_O, NULL},
    {NULL, NULL, 0, NULL}  // Sentinel
};

static PyModuleDef c_min_method_module = {
    PyModuleDef_HEAD_INIT,
    "minimal_method_module",
    NULL,
    -1,
    MyModuleMethods
};

PyMODINIT_FUNC
PyInit_c_min_method_module(void)
{
    return PyModule_Create(&c_min_method_module);
}
"""


ccompile("c_min_method_module", code)
from c_min_method_module import as_double

# ~igv~: function_root_count_at
def count(num):
    print("###### NUM: " + str(num))
    for i in range(num):
        if 42 != as_double(42):
            raise RuntimeError()
    return 0


def measure(num):
    result = count(num)
    return result


def __benchmark__(num=1000000):
    return measure(num)
