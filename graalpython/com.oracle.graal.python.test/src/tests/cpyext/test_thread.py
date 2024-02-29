# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import threading

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare, CPyExtType

__dir__ = __file__.rpartition("/")[0]


class TestPyThread(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyThread, self).compile_module(name)

    # TODO test that it's really thread-local
    test_PyThread_tss = CPyExtFunction(
        lambda args: args[0],
        lambda: (
            ('asdf',),
        ),
        code='''
        PyObject* test_PyThread_tss_functions(PyObject *object) {
            PyObject *res = NULL;
            Py_tss_t *local = PyThread_tss_alloc();
            if (!local) {
                return NULL;
            }
            if (PyThread_tss_create(local)) {
                goto end;
            }
            if (!PyThread_tss_is_created(local)) {
                PyErr_SetString(PyExc_AssertionError, "PyThread_tss_is_created returned false");
                goto end;
            }
            if (PyThread_tss_set(local, object)) {
                goto end;
            }
            res = PyThread_tss_get(local);
        end:
            PyThread_tss_free(local);
            return res;
        }
        ''',
        resultspec="O",
        argspec='O',
        arguments=["PyObject* object"],
        callfunction="test_PyThread_tss_functions",
        cmpfunc=unhandled_error_compare
    )


class TestNativeThread:
    def test_register_new_thread(self):
        TestThread = CPyExtType(
            name="TestThread",
            includes="#include <pthread.h>",
            code=r'''
            void* thread_entrypoint(void* arg) {
                PyObject* callable = (PyObject*)arg;
                PyGILState_STATE gstate;
                gstate = PyGILState_Ensure();
                if (!PyObject_CallNoArgs(callable)) {
                    PyErr_WriteUnraisable(callable);
                }
                if (PyThreadState_Get() == NULL || PyThreadState_Get() == NULL) {
                    PyErr_WriteUnraisable(callable);
                }
                PyGILState_Release(gstate);
                return NULL;
            }
            PyObject* run_in_thread(PyObject* self, PyObject* callable) {
                Py_BEGIN_ALLOW_THREADS;
                pthread_t thread;
                pthread_create(&thread, NULL, thread_entrypoint, callable);
                pthread_join(thread, NULL);
                Py_END_ALLOW_THREADS;
                Py_RETURN_NONE;
            }
            ''',
            tp_methods='{"run_in_thread", (PyCFunction)run_in_thread, METH_O | METH_STATIC, ""}'
        )

        thread = None

        def callable():
            nonlocal thread
            thread = threading.current_thread()

        TestThread.run_in_thread(callable)

        assert thread
        assert thread is not threading.current_thread()
