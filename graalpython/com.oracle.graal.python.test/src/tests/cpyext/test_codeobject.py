# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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


def example_generator():
    # Some random code to make sure we excercise the bytecode index translation
    a = 1
    if a == 2:
        yield 2
    yield 1


gen = example_generator()
next(gen)
example_generator_frame = gen.gi_frame


def example_function():
    return 1


def reference_PyCode_Addr2Line(args):
    code, lasti = args
    if lasti >= 0:
        return list(code.co_positions())[lasti // 2][0]
    return code.co_firstlineno


class DummyClass():
    def foo(self):
        return 0


DummyInstance = DummyClass()
DictInstance = {}


class TestCodeobject(CPyExtTestCase):

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

    test_PyCode_NewWithPosOnlyArgs_native_tuples = CPyExtFunction(
        lambda args: args,
        lambda: (
            tuple(),
        ),
        code="""
        static PyObject* make_tuple(const char *a, const char *b, const char *c) {
            PyObject *tuple = PyTuple_New(3);
            if (tuple == NULL) {
                return NULL;
            }
            PyObject *item0 = PyUnicode_FromString(a);
            PyObject *item1 = PyUnicode_FromString(b);
            PyObject *item2 = PyUnicode_FromString(c);
            if (item0 == NULL || item1 == NULL || item2 == NULL) {
                Py_XDECREF(item0);
                Py_XDECREF(item1);
                Py_XDECREF(item2);
                Py_DECREF(tuple);
                return NULL;
            }
            PyTuple_SET_ITEM(tuple, 0, item0);
            PyTuple_SET_ITEM(tuple, 1, item1);
            PyTuple_SET_ITEM(tuple, 2, item2);
            return tuple;
        }

        static PyCodeObject* wrap_PyCode_NewWithPosOnlyArgs_native_tuples(PyObject* ignored) {
            PyObject *code = PyBytes_FromStringAndSize("", 0);
            PyObject *consts = PyTuple_New(0);
            PyObject *names = PyTuple_New(0);
            PyObject *varnames = make_tuple("a", "b", "c");
            PyObject *freevars = PyTuple_New(0);
            PyObject *cellvars = PyTuple_New(0);
            PyObject *filename = PyUnicode_FromString("filename");
            PyObject *name = PyUnicode_FromString("name");
            PyObject *qualname = PyUnicode_FromString("module.name");
            PyObject *linetable = PyBytes_FromStringAndSize("", 0);
            PyObject *exceptiontable = PyBytes_FromStringAndSize("", 0);
            PyCodeObject *result = NULL;

            if (code == NULL || consts == NULL || names == NULL || varnames == NULL ||
                freevars == NULL || cellvars == NULL || filename == NULL || name == NULL ||
                qualname == NULL || linetable == NULL || exceptiontable == NULL) {
                goto done;
            }
            result = PyUnstable_Code_NewWithPosOnlyArgs(
                            1, 0, 2, 3, 4, 0,
                            code, consts, names, varnames,
                            freevars, cellvars,
                            filename, name, qualname,
                            1, linetable, exceptiontable);

        done:
            Py_XDECREF(code);
            Py_XDECREF(consts);
            Py_XDECREF(names);
            Py_XDECREF(varnames);
            Py_XDECREF(freevars);
            Py_XDECREF(cellvars);
            Py_XDECREF(filename);
            Py_XDECREF(name);
            Py_XDECREF(qualname);
            Py_XDECREF(linetable);
            Py_XDECREF(exceptiontable);
            return result;
        }
        """,
        resultspec="O",
        resulttype="PyCodeObject*",
        argspec="",
        arguments=["PyObject* ignored"],
        callfunction="wrap_PyCode_NewWithPosOnlyArgs_native_tuples",
        cmpfunc=lambda cr, pr: isinstance(cr, types.CodeType),
    )

    test_PyCode_Addr2Line = CPyExtFunction(
        reference_PyCode_Addr2Line,
        lambda: (
            (example_function.__code__, -1),
            # CPython return firstlineno for 0, which doesn't make much sense
            # (example_function.__code__, 0),
            (example_generator.__code__, example_generator_frame.f_lasti),
        ),
        code='''
            int wrap_PyCode_Addr2Line(PyObject* code, int lasti) {
                return PyCode_Addr2Line((PyCodeObject*)code, lasti);
            }
            ''',
        resultspec="i",
        argspec="Oi",
        arguments=["PyObject* code", "int bci"],
        callfunction="wrap_PyCode_Addr2Line",
    )
