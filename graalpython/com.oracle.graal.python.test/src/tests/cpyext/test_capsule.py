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
import gc
import os
import time

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare, CPyExtType


class TestPyCapsule(CPyExtTestCase):
    test_PyCapsule_CheckExact = CPyExtFunction(
        lambda args: True,
        lambda: (
            ("hello", 0xDEADBEEF),
        ),
        code='''int wrap_PyCapsule_Check(char * name, Py_ssize_t ptr) {
            PyObject* capsule = PyCapsule_New((void *)ptr, name, NULL);
            return PyCapsule_CheckExact(capsule);
        }
        ''',
        resultspec="i",
        argspec='sn',
        arguments=["char* name", "Py_ssize_t ptr"],
        callfunction="wrap_PyCapsule_Check",
        cmpfunc=unhandled_error_compare
    )

    test_PyCapsule_GetPointer = CPyExtFunction(
        lambda args: True,
        lambda: (
            ("hello", 0xDEADBEEF),
        ),
        code='''int wrap_PyCapsule_Check(char * name, Py_ssize_t ptr) {
            PyObject* capsule = PyCapsule_New((void *)ptr, name, NULL);
            return PyCapsule_GetPointer(capsule, name) == (void*)ptr;
        }
        ''',
        resultspec="i",
        argspec='sn',
        arguments=["char* name", "Py_ssize_t ptr"],
        callfunction="wrap_PyCapsule_Check",
        cmpfunc=unhandled_error_compare
    )

    test_PyCapsule_GetName = CPyExtFunction(
        lambda args: True,
        lambda: (
            ("hello",),
        ),
        # Test that the returned name is pointer-identical, pybind11 relies on that
        code='''int wrap_PyCapsule_Check(char * name) {
            PyObject* capsule = PyCapsule_New((void *)1, name, NULL);
            return PyCapsule_GetName(capsule) == name;
        }
        ''',
        resultspec="i",
        argspec='s',
        arguments=["char* name"],
        callfunction="wrap_PyCapsule_Check",
        cmpfunc=unhandled_error_compare
    )

    test_PyCapsule_SetName = CPyExtFunction(
        lambda args: True,
        lambda: (
            ("hello",),
        ),
        # Test that the returned name is pointer-identical, pybind11 relies on that
        code='''int wrap_PyCapsule_Check(char * name) {
            PyObject* capsule = PyCapsule_New((void *)1, NULL, NULL);
            PyCapsule_SetName(capsule, name);
            return PyCapsule_GetName(capsule) == name;
        }
        ''',
        resultspec="i",
        argspec='s',
        arguments=["char* name"],
        callfunction="wrap_PyCapsule_Check",
        cmpfunc=unhandled_error_compare
    )

    test_PyCapsule_GetContext = CPyExtFunction(
        lambda args: True,
        lambda: (
            ("hello", 0xDEADBEEF),
        ),
        code='''int wrap_PyCapsule_Check(char * name, Py_ssize_t ptr) {
            PyObject* capsule = PyCapsule_New((void *)ptr, name, NULL);
            return PyCapsule_GetContext(capsule) == NULL;
        }
        ''',
        resultspec="i",
        argspec='sn',
        arguments=["char* name", "Py_ssize_t ptr"],
        callfunction="wrap_PyCapsule_Check",
        cmpfunc=unhandled_error_compare
    )

    test_PyCapsule_SetContext = CPyExtFunction(
        lambda args: args[1],
        lambda: (
            ("hello", 0xDEADBEEF),
        ),
        code='''Py_ssize_t wrap_PyCapsule_SetContext(char * name, Py_ssize_t ptr) {
            PyObject* capsule = PyCapsule_New((void*)ptr, name, NULL);
            PyCapsule_SetContext(capsule, (void*)ptr);
            return (Py_ssize_t) PyCapsule_GetContext(capsule);
        }
        ''',
        resultspec="n",
        argspec='sn',
        arguments=["char* name", "Py_ssize_t ptr"],
        callfunction="wrap_PyCapsule_SetContext",
        cmpfunc=unhandled_error_compare
    )

    if os.environ.get('GRAALPYTEST_RUN_GC_TESTS'):
        def test_capsule_destructor(self):
            Tester = CPyExtType(
                "CapsuleDestructorTester",
                code="""
                static void capsule_destructor(PyObject* capsule) {
                    PyObject* contents = (PyObject*) PyCapsule_GetPointer(capsule, "capsule");
                    assert(PyDict_Check(contents));
                    PyDict_SetItemString(contents, "destructor_was_here", Py_NewRef(Py_True));
                    Py_DECREF(contents);
                }
                
                static PyObject* create_capsule(PyObject* unused, PyObject* contents) {
                    return PyCapsule_New(Py_NewRef(contents), "capsule", capsule_destructor);
                }
                """,
                tp_methods='{"create_capsule", (PyCFunction)create_capsule, METH_O | METH_STATIC, NULL}',
            )
            d = {}
            capsule = Tester.create_capsule(d)
            assert capsule
            assert not d
            del capsule
            start = time.time()
            while "destructor_was_here" not in d:
                if time.time() - start > 60:
                    raise AssertionError("Capsule destructor didn't execute within timeout")
                gc.collect()
                time.sleep(0.01)
