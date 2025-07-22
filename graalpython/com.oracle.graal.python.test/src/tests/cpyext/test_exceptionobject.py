# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import unittest

from . import CPyExtType, CPyExtTestCase, CPyExtFunction

try:
    raise TypeError
except TypeError as e:
    TB = sys.exc_info()[2]
    exception_with_traceback = e


def raise_exception_with_cause():
    try:
        raise RuntimeError()
    except RuntimeError as e:
        exc = e
    try:
        raise IndexError from exc
    except IndexError as e:
        return e


def raise_exception_without_cause():
    try:
        raise RuntimeError()
    except RuntimeError as e:
        return e


exception_with_cause = AssertionError()
exception_with_cause.__cause__ = NameError()
exception_with_context = AssertionError()
exception_with_context.__context__ = AttributeError()

class TestExceptionobjectFunctions(CPyExtTestCase):

    test_PyException_SetTraceback = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            (AssertionError(), TB),
            (exception_with_traceback, None),
        ),
        code='''
        static PyObject* wrap_PyException_SetTraceback(PyObject* exc, PyObject* traceback) {
            PyException_SetTraceback(exc, traceback);
            traceback = PyException_GetTraceback(exc);
            if (traceback == NULL) {
                Py_RETURN_NONE;
            } else {
                return traceback;
            }
        }
        ''',
        callfunction='wrap_PyException_SetTraceback',
        argspec='OO',
        arguments=['PyObject* exc', 'PyObject* traceback'],
        resultspec='O',
    )

    test_PyException_SetCause = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            (AssertionError(), NameError()),
            (exception_with_context, None),
        ),
        code='''
        static PyObject* wrap_PyException_SetCause(PyObject* exc, PyObject* cause) {
            PyException_SetCause(exc, cause != Py_None ? cause : NULL);
            cause = PyException_GetCause(exc);
            if (cause == NULL) {
                Py_RETURN_NONE;
            } else {
                return cause;
            }
        }
        ''',
        callfunction='wrap_PyException_SetCause',
        argspec='OO',
        arguments=['PyObject* exc', 'PyObject* cause'],
        resultspec='O',
    )

    test_PyException_SetContext = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            (AssertionError(), NameError()),
            (exception_with_cause, None),
        ),
        code='''
        static PyObject* wrap_PyException_SetContext(PyObject* exc, PyObject* context) {
            PyException_SetContext(exc, context != Py_None ? context : NULL);
            context = PyException_GetContext(exc);
            if (context == NULL) {
                Py_RETURN_NONE;
            } else {
                return context;
            }
        }
        ''',
        callfunction='wrap_PyException_SetContext',
        argspec='OO',
        arguments=['PyObject* exc', 'PyObject* context'],
        resultspec='O',
    )


class TestExceptionObjectAccessors(unittest.TestCase):
    def test_PyException_GetCause(self):
        TestGetCause = CPyExtType("TestGetCause",
                             """
                             PyObject* get_cause(PyObject* self, PyObject* e) {
                                 PyObject *cause = PyException_GetCause(e);
                                 if (cause == NULL) {
                                     return PyUnicode_FromString("NULL");
                                 } else {
                                     return cause;
                                 }
                             }
                             """,
                             tp_methods='{"get_cause", (PyCFunction)get_cause, METH_O, ""}'
        )
        tester = TestGetCause()
        e = raise_exception_with_cause()
        assert tester.get_cause(e) == e.__cause__
        e = raise_exception_without_cause()
        assert tester.get_cause(e) == "NULL"

    def test_PyException_GetTraceback(self):
        TestGetTraceback = CPyExtType("TestGetTraceback",
                             """
                             PyObject* get_traceback(PyObject* self, PyObject* e) {
                                 PyObject *traceback = PyException_GetTraceback(e);
                                 if (traceback == NULL) {
                                     return PyUnicode_FromString("NULL");
                                 } else {
                                     return traceback;
                                 }
                             }
                             """,
                             tp_methods='{"get_traceback", (PyCFunction)get_traceback, METH_O, ""}'
        )
        tester = TestGetTraceback()
        e = raise_exception_with_cause()
        assert tester.get_traceback(e) == e.__traceback__
        assert tester.get_traceback(e.__cause__) == e.__cause__.__traceback__
