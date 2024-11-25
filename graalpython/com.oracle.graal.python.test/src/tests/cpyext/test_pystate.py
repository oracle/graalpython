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

from . import CPyExtTestCase, CPyExtFunction, CPyExtType


class TestPystate(CPyExtTestCase):
    test_PyThreadState_GetDict = CPyExtFunction(
        lambda args: type({}),
        lambda: (
            tuple(),
        ),
        code='''PyObject* wrap_PyThreadState_GetDict(PyObject* ignored) {
            return (PyObject*)Py_TYPE(PyThreadState_GetDict());
        }
        ''',
        resultspec="O",
        argspec="",
        arguments=["PyObject* ignored"],
        callfunction="wrap_PyThreadState_GetDict",
    )

    def test_PyThreadState_GetFrame(self):
        Tester = CPyExtType(
            "PyThreadState_GetFrameTester",
            code="""
            static PyObject* get_frame(PyObject* unused) {
                return (PyObject*)PyThreadState_GetFrame(PyThreadState_GET());
            }
            """,
            tp_methods='{"get_frame", (PyCFunction)get_frame, METH_NOARGS | METH_STATIC, NULL}',
        )
        assert Tester.get_frame() is sys._getframe(0)

    # This seems to get the native extensions into some inconsistent state on GraalPy, giving:
    # refcnt below zero during managed adjustment for 0000aaae18fca780 (9 0000000000000009 - 10)
    def ignored_test_SetAsyncExc(self):
        SetAsyncExcCaller = CPyExtType(
            "SetAsyncExcCaller",
            """
            static PyObject* trigger_ex(PyObject *cls, PyObject *args) {
                long thread_id;
                PyObject *ex;
                if (!PyArg_ParseTuple(args, "lO", &thread_id, &ex)) {
                    return NULL;
                }
                PyThreadState_SetAsyncExc(thread_id, ex);
                return PyLong_FromLong(42);
            }
            """,
            tp_methods='{"trigger_ex", (PyCFunction)trigger_ex, METH_VARARGS | METH_STATIC, ""}',
        )

        import threading
        start = threading.Barrier(2, timeout=20)

        caught_ex = None
        def other_thread():
            try:
                start.wait() # ensure we are in the try, before raising
                r = 0
                for i in range(1, 1000000000):
                    for j in range(i, 1000000000):
                        r += j / i
            except Exception as e:
                nonlocal caught_ex
                caught_ex = e


        t = threading.Thread(target=other_thread)
        t.start()

        start.wait()
        SetAsyncExcCaller.trigger_ex(t.ident, Exception("test my message"))
        t.join()

        assert "test my message" in str(caught_ex)
