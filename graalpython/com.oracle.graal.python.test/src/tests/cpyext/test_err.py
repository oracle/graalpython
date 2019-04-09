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
import warnings
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, CPyExtFunctionVoid, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


def _reference_setstring(args):
    raise args[0](args[1])


def _reference_setnone(args):
    raise args[0]()


def _reference_format(args):
    raise args[0](args[1].lower() % args[2:])


def _reference_fetch(args):
    try:
        raise args[0]
    except:
        return sys.exc_info()[0]


def _is_exception_class(exc):
    return isinstance(exc, type) and issubclass(exc, BaseException)


def _reference_givenexceptionmatches(args):
    err = args[0]
    exc = args[1]
    if isinstance(exc, tuple):
        for e in exc:
            if _reference_givenexceptionmatches((err, e)):
                return 1
        return 0
    if isinstance(err, BaseException):
        err = type(err)
    if _is_exception_class(err) and _is_exception_class(exc):
        return issubclass(err, exc)
    return exc is err


def _reference_nomemory(args):
    raise MemoryError


class Dummy:
    pass


class TestPyNumber(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyNumber, self).compile_module(name)

    test_PyErr_SetString = CPyExtFunctionVoid(
        _reference_setstring,
        lambda: (
            (ValueError, "hello"),
            (TypeError, "world"),
            (KeyError, "key"),
        ),
        resultspec="O",
        argspec='Os',
        arguments=["PyObject* v", "char* msg"],
        resultval="NULL",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_SetObject = CPyExtFunctionVoid(
        _reference_setstring,
        lambda: (
            (ValueError, "hello"),
            (TypeError, "world"),
            (KeyError, "key"),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* msg"],
        resultval="NULL",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_SetNone = CPyExtFunctionVoid(
        _reference_setnone,
        lambda: (
            (ValueError,),
            (TypeError,),
            (KeyError,),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        resultval="NULL",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_Format = CPyExtFunctionVoid(
        _reference_format,
        lambda: (
            (ValueError, "hello %S %S", "beautiful", "world"),
            (TypeError, "world %S %S", "", ""),
            (KeyError, "key %S %S", "", ""),
            (KeyError, "unknown key: %S %S", "some_key", ""),
        ),
        resultspec="O",
        argspec='OsOO',
        arguments=["PyObject* v", "char* msg", "PyObject* arg0", "PyObject* arg1"],
        resultval="NULL",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_PrintEx = CPyExtFunction(
        lambda args: None,
        lambda: (
            (True,),
        ),
        code="""PyObject* wrap_PyErr_PrintEx(int n) {
             PyErr_SetString(PyExc_KeyError, "unknown key whatsoever");
             PyErr_PrintEx(n);
             return Py_None;
         }
         """,
        resultspec="O",
        argspec='i',
        arguments=["int n"],
        callfunction="wrap_PyErr_PrintEx",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_GivenExceptionMatches = CPyExtFunction(
        _reference_givenexceptionmatches,
        lambda: (
            (ValueError(), ValueError),
            (ValueError(), BaseException),
            (ValueError(), KeyError),
            (ValueError(), (KeyError, SystemError, OverflowError)),
            (ValueError(), Dummy),
            (ValueError(), Dummy()),
            (Dummy(), Dummy()),
            (Dummy(), Dummy),
            (Dummy(), KeyError),
        ),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* err", "PyObject* exc"],
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_GivenExceptionMatchesNative = CPyExtFunction(
        lambda args: args[2],
        lambda: (
            # ValueError = 0
            # KeyError = 1
            (ValueError, 0, True),
            (ValueError, 1, False),
            (KeyError, 0, False),
            (KeyError, 1, True),
            (Dummy, 0, False),
            (Dummy, 1, False),
        ),
        code="""int PyErr_GivenExceptionMatchesNative(PyObject* exc, int selector, int unused) {
            switch(selector) {
            case 0:
                return exc == PyExc_ValueError;
            case 1:
                return exc == PyExc_KeyError;
            }
            return 0;
        }
        """,
        resultspec="i",
        argspec='Oii',
        arguments=["PyObject* exc", "int selector", "int unused"],
        callfunction="PyErr_GivenExceptionMatchesNative",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_Occurred = CPyExtFunction(
        lambda args: args[0] if _is_exception_class(args[0]) else SystemError,
        lambda: (
            (ValueError, "hello"),
            (KeyError, "world"),
            (ValueError, Dummy()),
            (Dummy, ""),
        ),
        code="""PyObject* wrap_PyErr_Occurred(PyObject* exc, PyObject* msg) {
            PyObject* result;
            PyErr_SetObject(exc, msg);
            result = PyErr_Occurred();
            PyErr_Clear();
            return result;
        }
        """,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* err", "PyObject* msg"],
        callfunction="wrap_PyErr_Occurred",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_ExceptionMatches = CPyExtFunction(
        _reference_givenexceptionmatches,
        lambda: (
            (ValueError, ValueError),
            (ValueError, BaseException),
            (ValueError, KeyError),
            (ValueError, (KeyError, SystemError, OverflowError)),
            (ValueError, Dummy),
            (ValueError, Dummy()),
        ),
        code="""int wrap_PyErr_ExceptionMatches(PyObject* err, PyObject* exc) {
            int res;
            PyErr_SetNone(err);
            res = PyErr_ExceptionMatches(exc);
            PyErr_Clear();
            return res;
        }
        """,
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* err", "PyObject* exc"],
        callfunction="wrap_PyErr_ExceptionMatches",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_WarnEx = CPyExtFunctionVoid(
        lambda args: warnings.warn(args[1], args[0], args[2]),
        lambda: (
            (UserWarning, "custom warning", 1),
        ),
        resultspec="O",
        argspec='Osn',
        arguments=["PyObject* category", "char* msg", "Py_ssize_t level"],
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_NoMemory = CPyExtFunctionVoid(
        _reference_nomemory,
        lambda: (
            tuple(),
        ),
        resultspec="O",
        argspec='',
        argumentnames="",
        arguments=["PyObject* dummy"],
        resultval="NULL",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_WriteUnraisable = CPyExtFunctionVoid(
        lambda args: None,
        lambda: (
            ("hello",),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_Fetch = CPyExtFunction(
        _reference_fetch,
        lambda: (
            (ValueError,),
            (TypeError,),
            (KeyError,),
        ),
        code="""PyObject* wrap_PyErr_Fetch(PyObject* exception_type) {
            PyObject* typ = NULL;
            PyObject* val = NULL;
            PyObject* tb = NULL;
            Py_ssize_t size = 3;
            PyErr_SetNone(exception_type);
            PyErr_Fetch(&typ, &val, &tb);
            return typ;
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* exception_type"],
        callfunction="wrap_PyErr_Fetch",
        cmpfunc=unhandled_error_compare
    )
