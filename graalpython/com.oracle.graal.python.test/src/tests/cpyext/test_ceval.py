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

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare, CPyExtType


class TestCeval(CPyExtTestCase):
    test_Py_EnterLeaveRecursiveCall = CPyExtFunction(
        # We don't know the exact limit on CPython since it uses the counter in
        # the interpreter as well.
        lambda args: None if args[0] < 800 else RecursionError(args[1]),
        lambda: (
            (1, ": one"),
            (500, ": five hundred"),
            (10000, ": ten thousand"),
        ),
        code="""
            PyObject* wrap_Py_EnterLeaveRecursiveCall(int n, char *where) {
                PyObject *exc_type = NULL;
                int i;
                for (i = 0; i < n && !exc_type; i++) {
                    if (Py_EnterRecursiveCall((const char *) where)) {
                        exc_type = Py_NewRef(PyErr_Occurred());
                    }
                }
                // restore because this state may influence other tests
                for (int j = 0; j < i; j++) {
                    Py_LeaveRecursiveCall();
                }
                if (exc_type) {
                    return exc_type;
                }
                Py_RETURN_NONE;
            }
        """,
        callfunction='wrap_Py_EnterLeaveRecursiveCall',
        resultspec="O",
        argspec='is',
        arguments=["int n", "char* where"],
        cmpfunc=unhandled_error_compare
    )

    def test_PyEval_GetGlobals(self):
        Tester = CPyExtType(
            "GetGlobalsTester",
            code="""
            static PyObject* get_globals(PyObject* unused) {
                return Py_NewRef(PyEval_GetGlobals());
            }
            """,
            tp_methods='{"get_globals", (PyCFunction)get_globals, METH_NOARGS | METH_STATIC, NULL}',
        )
        assert Tester.get_globals() is globals()
