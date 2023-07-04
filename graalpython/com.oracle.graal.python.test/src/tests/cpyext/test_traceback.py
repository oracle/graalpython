# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

from tests.cpyext import CPyExtType


class TestTraceback:
    def test_PyTraceBack_Here(self):
        Tester = CPyExtType(
            "TracebackTester1",
            """
            PyObject* test(PyObject* unused, PyObject* frame) {
                Py_INCREF(frame);
                PyErr_SetString(PyExc_RuntimeError, "test error");
                if (PyTraceBack_Here((PyFrameObject*)frame)) {
                    Py_DECREF(frame);
                    return NULL;
                }
                return NULL;
            }
            """,
            tp_methods='{"test", test, METH_O | METH_STATIC, ""}',
        )

        def fake_frame():
            return sys._getframe(0)

        frame = fake_frame()

        try:
            Tester.test(frame)
        except RuntimeError as e:
            assert e.__traceback__
            assert e.__traceback__.tb_next
            assert e.__traceback__.tb_next.tb_frame is frame
            assert e.__traceback__.tb_next.tb_next is None
        else:
            assert False, "Didn't raise exception"

    def test_PyTraceBack_Here_and_fetch(self):
        Tester = CPyExtType(
            "TracebackTester2",
            """
            PyObject* test(PyObject* unused, PyObject* frame) {
                Py_INCREF(frame);
                PyErr_SetString(PyExc_RuntimeError, "test error");
                if (PyTraceBack_Here((PyFrameObject*)frame)) {
                    Py_DECREF(frame);
                    return NULL;
                }
                PyObject *type, *value, *tb;
                PyErr_Fetch(&type, &value, &tb);
                Py_DECREF(type);
                Py_DECREF(value);
                return tb;
            }
            """,
            tp_methods='{"test", test, METH_O | METH_STATIC, ""}',
        )

        def fake_frame():
            return sys._getframe(0)

        frame = fake_frame()

        tb = Tester.test(frame)
        assert tb.tb_frame is frame
        assert tb.tb_next is None
