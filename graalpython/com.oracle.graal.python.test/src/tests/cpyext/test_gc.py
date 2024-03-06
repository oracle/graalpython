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

import gc
import sys
import time

from . import CPyExtType

__dir__ = __file__.rpartition("/")[0]

GRAALPYTHON_NATIVE = sys.implementation.name == 'graalpy' and __graalpython__.get_platform_id() == 'native'

# typedef PyObject * (*unaryfunc)(PyObject *);
# typedef PyObject * (*binaryfunc)(PyObject *, PyObject *);
# typedef PyObject * (*ternaryfunc)(PyObject *, PyObject *, PyObject *);
# typedef int (*inquiry)(PyObject *);
# typedef Py_ssize_t (*lenfunc)(PyObject *);
# typedef PyObject *(*ssizeargfunc)(PyObject *, Py_ssize_t);
# typedef PyObject *(*ssizessizeargfunc)(PyObject *, Py_ssize_t, Py_ssize_t);
# typedef int(*ssizeobjargproc)(PyObject *, Py_ssize_t, PyObject *);
# typedef int(*ssizessizeobjargproc)(PyObject *, Py_ssize_t, Py_ssize_t, PyObject *);
# typedef int(*objobjargproc)(PyObject *, PyObject *, PyObject *);
#
# typedef int (*objobjproc)(PyObject *, PyObject *);
# typedef int (*visitproc)(PyObject *, void *);
# typedef int (*traverseproc)(PyObject *, visitproc, void *);
#
#
# typedef PyObject *(*getattrfunc)(PyObject *, char *);
# typedef PyObject *(*getattrofunc)(PyObject *, PyObject *);
# typedef int (*setattrfunc)(PyObject *, char *, PyObject *);
# typedef int (*setattrofunc)(PyObject *, PyObject *, PyObject *);
# typedef PyObject *(*reprfunc)(PyObject *);
# typedef Py_hash_t (*hashfunc)(PyObject *);
# typedef PyObject *(*richcmpfunc) (PyObject *, PyObject *, int);
# typedef PyObject *(*getiterfunc) (PyObject *);
# typedef PyObject *(*iternextfunc) (PyObject *);
# typedef PyObject *(*descrgetfunc) (PyObject *, PyObject *, PyObject *);
# typedef int (*descrsetfunc) (PyObject *, PyObject *, PyObject *);
# typedef int (*initproc)(PyObject *, PyObject *, PyObject *);


GCTestClass = CPyExtType("GCTestClass",
        """
        static int new_cnt = 0;
        PyObject *test_new(struct _typeobject *a, PyObject *b, PyObject *c) {
            new_cnt++;
            return PyType_GenericNew(a, b, c);
        }
        static int alloc_cnt = 0;
        PyObject *test_alloc(struct _typeobject *a, Py_ssize_t b) {
            alloc_cnt++;
            return PyType_GenericAlloc(a, b);
        }
        static int free_cnt = 0;
        void test_free(void *a) {
            // printf("free\\n");
            free_cnt++;
            PyObject_Del(a);
        }
        static int dealloc_cnt = 0;
        void test_dealloc(PyObject *self) {
            // printf("dealloc\\n");
            dealloc_cnt++;
            Py_TYPE(self)->tp_free((PyObject *)self);
        }
        PyObject* getCounters(PyObject* a, PyObject* b) {
            return Py_BuildValue("(iiii)", new_cnt, alloc_cnt, free_cnt, dealloc_cnt);
        }
        PyObject* resetCounters(PyObject* a, PyObject* b) {
            new_cnt = 0;
            alloc_cnt = 0;
            free_cnt = 0;
            dealloc_cnt = 0;
            Py_RETURN_NONE;
        }
        """,
        tp_new = "test_new",
        tp_alloc = "test_alloc",
        tp_free = "test_free",
        tp_dealloc = "test_dealloc",
        tp_methods='{"getCounters", (PyCFunction)getCounters, METH_NOARGS | METH_STATIC, ""}, {"resetCounters", (PyCFunction)resetCounters, METH_NOARGS | METH_STATIC, ""}',
)

class TestGC1():

    def test_native_class(self):
        if GRAALPYTHON_NATIVE:
            gc.enable()
            GCTestClass.resetCounters()
            a = GCTestClass.getCounters()
            assert a == (0,0,0,0)
            o = GCTestClass()
            b = GCTestClass.getCounters()
            assert b == (1,1,0,0)
            del o
            for i in range(4):
                gc.collect()
                time.sleep(1)
            c = GCTestClass.getCounters()
            assert c == (1,1,1,1)

#
# class TestGC2(CPyExtTestCase):
#
#     test_simple = CPyExtFunction(
#         lambda args: (1, 0),
#         lambda: (({"foo": "bar"},), ),
#         code='''PyObject* wrap_simple(PyObject* o) {
#             Py_ssize_t r1 = Py_REFCNT(o);
#             Py_IncRef(o);
#             Py_ssize_t r2 = Py_REFCNT(o);
#             Py_DecRef(o);
#             Py_ssize_t r3 = Py_REFCNT(o);
#             return Py_BuildValue("(ii)", r2 - r1, r3 - r1);
#         }''',
#         resultspec="O",
#         argspec='O',
#         arguments=("PyObject* o", ),
#         callfunction="wrap_simple",
#     )
#
#
#     test_create = CPyExtFunction(
#         lambda args: (1, 0),
#         lambda: ((GCTestClass,), ),
#         code='''PyObject* wrap_simple(PyObject* o) {
#
#             Py_ssize_t r1 = Py_REFCNT(o);
#             Py_IncRef(o);
#             Py_ssize_t r2 = Py_REFCNT(o);
#             Py_DecRef(o);
#             Py_ssize_t r3 = Py_REFCNT(o);
#             return Py_BuildValue("(ii)", r2 - r1, r3 - r1);
#         }''',
#         resultspec="O",
#         argspec='O',
#         arguments=("PyObject* o", ),
#         callfunction="wrap_simple",
#     )
