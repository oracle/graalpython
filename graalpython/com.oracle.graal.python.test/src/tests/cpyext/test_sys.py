# Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

from . import (
    CPyExtFunction,
    CPyExtTestCase,
    compile_module_from_string,
    unhandled_error_compare,
)


def _reference_get_object(args):
    try:
        return getattr(sys, args[0])
    except AttributeError:
        raise SystemError # raised by PyBuildValue(..., NULL)
        
class TestPySys(CPyExtTestCase):
    def test_PySys_Audit(self):
        module = compile_module_from_string("""
            #define PY_SSIZE_T_CLEAN
            #include <Python.h>

            static PyObject* audit(PyObject* self, PyObject* Py_UNUSED(args)) {
                PyObject *value = PyUnicode_FromString("value");
                if (value == NULL) {
                    return NULL;
                }
                int res = PySys_Audit("graalpy.test_capi_audit", "Oi", value, 23);
                Py_DECREF(value);
                if (res < 0) {
                    return NULL;
                }
                return PyLong_FromLong(res);
            }

            static PyObject* audit_error(PyObject* self, PyObject* Py_UNUSED(args)) {
                PyObject *value = PyUnicode_FromString("value");
                if (value == NULL) {
                    return NULL;
                }
                int res = PySys_Audit("graalpy.test_capi_audit_error", "O", value);
                Py_DECREF(value);
                if (res < 0) {
                    return NULL;
                }
                return PyLong_FromLong(res);
            }

            static PyMethodDef methods[] = {
                {"audit", audit, METH_NOARGS, NULL},
                {"audit_error", audit_error, METH_NOARGS, NULL},
                {NULL, NULL, 0, NULL}
            };

            static struct PyModuleDef module = {
                PyModuleDef_HEAD_INIT, "test_sys_audit", NULL, -1, methods
            };

            PyMODINIT_FUNC PyInit_test_sys_audit(void) {
                return PyModule_Create(&module);
            }
        """, "test_sys_audit")

        seen = []

        def hook(event, args):
            if event == "graalpy.test_capi_audit":
                seen.append(args)

        sys.addaudithook(hook)

        self.assertEqual(module.audit(), 0)
        self.assertEqual(seen, [("value", 23)])

        class AuditError(Exception):
            pass

        def error_hook(event, args):
            if event == "graalpy.test_capi_audit_error":
                raise AuditError(args)

        sys.addaudithook(error_hook)

        with self.assertRaises(AuditError) as cm:
            module.audit_error()
        self.assertEqual(cm.exception.args[0], ("value",))

    test_PySys_GetObject = CPyExtFunction(
        _reference_get_object,
        lambda: (
            ("hello",),
            ("byteorder",),
        ),
        resultspec="O",
        argspec='s',
        arguments=["char* name"],
        cmpfunc=unhandled_error_compare
    )

    test_Interpreters = CPyExtFunction(
        lambda args: 1,
        lambda: (
            (),
        ),
        code="""PyObject* wrap_Interpreters() {
            return PyLong_FromLong(PyThreadState_Get()->interp == PyInterpreterState_Main());
        }
        """,
        resultspec="O",
        argspec='',
        arguments=[],
        callfunction="wrap_Interpreters",
        cmpfunc=unhandled_error_compare,
    )
