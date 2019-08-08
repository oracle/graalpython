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

from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


def _reference_getslice(args):
    t = args[0]
    start = args[1]
    end = args[2]
    return t[start:end]


class MyStr(str):

    def __init__(self, s):
        self.s = s

    def __repr__(self):
        return self.s


class TestPyTuple(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyTuple, self).compile_module(name)

    # PyTuple_Size
    test_PyTuple_Size = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            (tuple(),),
            ((1, 2, 3),),
            (("a", "b"),),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* tuple"],
    )

    # PyTuple_GET_SIZE
    test_PyTuple_GET_SIZE = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            (tuple(),),
            ((1, 2, 3),),
            (("a", "b"),),
            # no type checking, also accepts different objects
            ([1, 2, 3, 4],),
            ({"a": 1, "b":2},),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* tuple"],
    )

    # PyTuple_GetSlice
    test_PyTuple_GetSlice = CPyExtFunctionOutVars(
        _reference_getslice,
        lambda: (
            (tuple(), 0, 0),
            ((1, 2, 3), 0, 2),
            ((4, 5, 6), 1, 2),
            ((7, 8, 9), 2, 2),
        ),
        resultspec="O",
        argspec='Onn',
        arguments=["PyObject* tuple", "Py_ssize_t start", "Py_ssize_t end"],
        resulttype="PyObject*",
    )

    # PyTuple_Pack
    test_PyTuple_Pack = CPyExtFunctionOutVars(
        lambda vals: vals[1:],
        lambda: ((3, MyStr("hello"), MyStr("beautiful"), MyStr("world")),),
        resultspec="O",
        argspec='nOOO',
        arguments=["Py_ssize_t n", "PyObject* arg0", "PyObject* arg1", "PyObject* arg2"],
         resulttype="PyObject*",
    )

    # PyTuple_Check
    test_PyTuple_Check = CPyExtFunction(
        lambda args: isinstance(args[0], tuple),
        lambda: (
            (tuple(),),
            (("hello", "world"),),
            ((None,),),
            ([],),
            ({},),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    # PyTuple_Check
    test_PyTuple_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is tuple,
        lambda: (
            (tuple(),),
            (("hello", "world"),),
            ((None,),),
            ([],),
            ({},),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )
