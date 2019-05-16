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
from . import CPyExtType, CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


try:
    raise TypeError
except:
    TB = sys.exc_info()[2]


class TestExceptionobject(object):
    def test_exc_info(self):
        TestExcInfo = CPyExtType("TestExcInfo",
                             """
                             PyObject* get_exc_info(PyObject* self) {
                                 PyObject* typ;
                                 PyObject* val;
                                 PyObject* tb;
                                 PyErr_GetExcInfo(&typ, &val, &tb);
                                 Py_INCREF(typ);
                                 return typ;
                             }
                             """,
                             tp_methods='{"get_exc_info", (PyCFunction)get_exc_info, METH_NOARGS, ""}'
        )
        tester = TestExcInfo()
        try:
            raise IndexError
        except:
            exc_type = tester.get_exc_info()
            assert exc_type == IndexError

            # do a second time because this time we won't do a stack walk
            exc_type = tester.get_exc_info()
            assert exc_type == IndexError
        else:
            assert False

    def test_set_exc_info(self):
        TestSetExcInfo = CPyExtType("TestSetExcInfo",
                             """
                             PyObject* set_exc_info(PyObject* self, PyObject* args) {
                                 PyObject* typ = PyTuple_GetItem(args, 0);
                                 PyObject* val = PyTuple_GetItem(args, 1);
                                 PyObject* tb = PyTuple_GetItem(args, 2);
                                 PyObject* typ1 = NULL;
                                 PyObject* val1 = NULL;
                                 PyObject* tb1 = NULL;
            
                                 Py_XINCREF(typ);
                                 Py_XINCREF(val);
                                 Py_XINCREF(tb);
                                 PyErr_SetExcInfo(typ, val, tb);

                                 PyErr_GetExcInfo(&typ1, &val1, &tb1);
                                 // ignore the traceback for now
                                 if(typ == typ1 && val == val1) {
                                     return Py_True;
                                 }
                                 return Py_False;
                             }
                             """,
                             tp_methods='{"set_exc_info", (PyCFunction)set_exc_info, METH_O, ""}'
        )
        tester = TestSetExcInfo()
        try:
            raise IndexError
        except:
            typ, val, tb = sys.exc_info()
            assert typ == IndexError
            
            
            
            # overwrite exception info
            expected = (ValueError, ValueError(), None)
            res = tester.set_exc_info(expected)
            assert res
            
            # TODO uncomment once supported
            # actual = sys.exc_info()
            # assert actual == expected
        else:
            assert False

class TestExceptionobjectFunctions(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super().compile_module(name)

    test_PyException_SetTraceback = CPyExtFunction(
        lambda args: 0,
        lambda: (
            (
                AssertionError(), TB
            ),
        ),
        resultspec="i",
        argspec="OO",
        arguments=["PyObject* exc", "PyObject* tb"],
    )
