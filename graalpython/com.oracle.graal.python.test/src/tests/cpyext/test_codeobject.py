# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import types

from . import CPyExtTestCase, CPyExtFunction

__dir__ = __file__.rpartition("/")[0]


def example_function():
    return 1


class DummyClass():
    def foo(self):
        return 0


DummyInstance = DummyClass()
DictInstance = {}


class TestCodeobject(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super().compile_module(name)

    testmod = type(sys)("foo")

    test_PyCode_NewEmpty = CPyExtFunction(
        lambda args: args,
        lambda: (
            ("file.c", "myfunc", 54),
        ),
        resultspec="O",
        resulttype="PyCodeObject*",
        argspec="ssi",
        arguments=["char* filename", "char* funcname", "int firstlineno"],
        cmpfunc=lambda cr, pr: pr[0] == cr.co_filename and pr[1] == cr.co_name and pr[2] == cr.co_firstlineno,
    )

    test_PyCode_New = CPyExtFunction(
        lambda args: args,
        lambda: (
            (
                1, 2,
                3, 4, 0,
                b"", tuple(), tuple(),
                ("a", "b", "c"), tuple(), tuple(),
                "filename", "name", "module.name",
                1,
                b"", b"",
            ),
        ),
        resultspec="O",
        resulttype="PyCodeObject*",
        argspec="iiiiiOOOOOOOOOiOO",
        arguments=[
            "int argcount", "int kwonlyargcount",
            "int nlocals", "int stacksize", "int flags",
            "PyObject* code", "PyObject* consts", "PyObject* names",
            "PyObject* varnames", "PyObject* freevars", "PyObject* cellvars",
            "PyObject* filename", "PyObject* name", "PyObject* qualname",
            "int firstlineno",
            "PyObject* linetable", "PyObject* exceptiontable",
        ],
        cmpfunc=lambda cr, pr: isinstance(cr, types.CodeType),
    )
    
    test_PyCode_NewWithPosOnlyArgs = CPyExtFunction(
        lambda args: args,
        lambda: (
            (
                1, 0, 2,
                3, 4, 0,
                b"", tuple(), tuple(),
                ("a", "b", "c"), tuple(), tuple(),
                "filename", "name", "module.name",
                1,
                b"", b"",
            ),
        ),
        resultspec="O",
        resulttype="PyCodeObject*",
        argspec="iiiiiiOOOOOOOOOiOO",
        arguments=[
            "int argcount", "int posonlyargcount", "int kwonlyargcount",
            "int nlocals", "int stacksize", "int flags",
            "PyObject* code", "PyObject* consts", "PyObject* names",
            "PyObject* varnames", "PyObject* freevars", "PyObject* cellvars",
            "PyObject* filename", "PyObject* name", "PyObject* qualname",
            "int firstlineno",
            "PyObject* lnotab", "PyObject* exceptiontable",
        ],
        cmpfunc=lambda cr, pr: isinstance(cr, types.CodeType),
    )

    test_PyCode_Addr2Line = CPyExtFunction(
        lambda args: args[0].co_firstlineno + int(args[1] >= 0),
        lambda: (
            (example_function.__code__, -1),
            # CPython return firstlineno for 0, which doesn't make much sense
            # (example_function.__code__, 0),
            (example_function.__code__, 2),
        ),
        code='''int wrap_PyCode_Addr2Line(PyObject* code, int bci) {
                return PyCode_Addr2Line((PyCodeObject*)code, bci);
            }
            ''',
        resultspec="i",
        argspec="Oi",
        arguments=["PyObject* code", "int bci"],
        callfunction="wrap_PyCode_Addr2Line",
    )
