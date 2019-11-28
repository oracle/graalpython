# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


def _reference_format_specifier_w_star(args):
    bytes_like = args[0]
    bytes_like[0] = ord('a')
    return bytes_like


def _reference_typecheck(args):
    if not isinstance(args[0][0], str):
        raise TypeError
    return args[0][0]


class TestModsupport(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super().compile_module(name)

    testmod = type(sys)("foo")

    test_PyModule_AddStringConstant = CPyExtFunction(
        lambda args: getattr(args[0], args[1]) == args[2],
        lambda: (
            (TestModsupport.testmod, "key", "value"),
        ),
        resultspec="i",
        argspec="Oss",
        arguments=["PyObject* m", "const char* name", "const char* value"],
        cmpfunc=lambda cr, pr: cr == 0 and pr is True
    )

    test_format_specifier_w_star = CPyExtFunction(
        _reference_format_specifier_w_star,
        lambda: (
            (bytearray(b'helloworld'),),
        ),
        code='''PyObject* wrap_PyArg_ParseTuple(PyObject* bytesLike) {
            Py_buffer buf;
            PyObject* args = PyTuple_New(1);
            char *ptr = NULL;
            PyTuple_SetItem(args, 0, bytesLike);
            Py_INCREF(args);
            if (PyArg_ParseTuple(args, "w*", &buf) == 0) {
                return NULL;
            }
            ptr = (char*) (buf.buf);
            ptr[0] = 'a';
            Py_DECREF(args);
            return bytesLike;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* bytesLike"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_O_conv = CPyExtFunction(
        lambda args: True if args[0][0] else False,
        lambda: (
            ((b'helloworld', ),),
            ((b'', ),),
            (([0, 1, 2], ),),
            (([], ),),
        ),
        code='''
        static int convert_seq(PyObject* obj, void* out) {
            PyObject **objOut = (PyObject **)out;
            *objOut = PySequence_Size(obj) == 0 ? Py_False : Py_True;
            return 1;
        }
        
        static PyObject* wrap_PyArg_ParseTuple(PyObject* argTuple) {
            PyObject* out = NULL;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "O&", convert_seq, &out) == 0) {
                return NULL;
            }
            Py_XINCREF(out);
            return out;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )

    test_parseargs_O_typecheck = CPyExtFunction(
        _reference_typecheck,
        lambda: (
            (('helloworld', ),),
            (('', ),),
            (([0, 1, 2], ),),
            (([], ),),
        ),
        code='''
        static PyObject* wrap_PyArg_ParseTuple(PyObject* argTuple) {
            PyObject* out = NULL;
            Py_INCREF(argTuple);
            if (PyArg_ParseTuple(argTuple, "O!", &PyUnicode_Type, &out) == 0) {
                return NULL;
            }
            Py_XINCREF(out);
            return out;
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* argTuple"],
        callfunction="wrap_PyArg_ParseTuple",
        cmpfunc=unhandled_error_compare
    )
