# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import sys
from unittest import skipUnless
from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare, GRAALPYTHON


# Test for the presence of a few custom symbols we have that are considered
# "public" because we use them in our patches to C extensions
class TestPublicSymbols(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super().compile_module(name)

    test_PyO3_symbols = CPyExtFunction(
        lambda args: True,
        lambda: (
            ((
                "_Py_FalseStructReference",
                "_Py_TrueStructReference",
                "_Py_EllipsisObjectReference",
                "_Py_SIZE",
                "_Py_REFCNT",
                "_Py_TYPE",
                "_Py_NoneStructReference",
                "_Py_NotImplementedStructReference",
                "_PyList_SET_ITEM",
            ),),
        ),
        code='''
        #include <dlfcn.h>

        PyObject* use_symbols_in_pyo3(PyObject* symbols) {
            void *sym;
            Py_ssize_t len = PyObject_Size(symbols);
            for (Py_ssize_t i = 0; i < len; i++) {
                sym = NULL;
                PyObject *o = PySequence_GetItem(symbols, i);
                const char* s = PyUnicode_AsUTF8(o);
                sym = dlsym(RTLD_DEFAULT, s);
                PyObject *r;
                if (sym == NULL) {
                    r = PyUnicode_FromFormat("Missing %s symbol in global namespace", s);
                }
                Py_DECREF(o);
                if (sym == NULL) {
                    return r;
                }
            }
            Py_RETURN_TRUE;
        }
        ''',
        resultspec="O",
        argspec='O',
        callfunction="use_symbols_in_pyo3",
        arguments=["PyObject* a"],
        cmpfunc=unhandled_error_compare
    )

    if not GRAALPYTHON or sys.platform != "linux":
        del test_PyO3_symbols
