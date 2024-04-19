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


class TestGCRefCycles:
    def _trigger_gc(self):
        gc.collect()
        for i in range(4 if GRAALPYTHON_NATIVE else 1):
            time.sleep(0.5)
            gc.collect()

    def test_cycle_with_native_objects(self):
        TestCycle0 = CPyExtType("TestCycle0",
                                '''
                                #define N 16
                                static int freed[N];

                                static int tc0_init(TestCycle0Object* self, PyObject* args, PyObject *kwargs) {
                                    if (!PyArg_ParseTuple(args, "i", &self->idx)) {
                                        return -1;
                                    }
                                    if (self->idx < 0 || self->idx >= N) {
                                        PyErr_Format(PyExc_ValueError, "invalid index; must be between 0 and %d", N);
                                        return -1;
                                    }
                                    freed[self->idx] = 0;
                                    return 0;
                                }

                                static void tc0_clear(TestCycle0Object* self) {
                                    printf("clear of %d called\\n", self->idx);
                                    Py_CLEAR(self->other);
                                }

                                static void tc0_dealloc(TestCycle0Object* self) {
                                    printf("dealloc of %d called\\n", self->idx);
                                    PyObject_GC_UnTrack(self);
                                    tc0_clear(self);
                                    freed[self->idx] = 1;
                                    PyObject_GC_Del(self);
                                }

                                static int tc0_traverse(TestCycle0Object* self, visitproc visit, void* arg) {
                                    printf("traverse of %d called -- refcnt = %zd\\n", self->idx, Py_REFCNT(self));
                                    if (self->other) {
                                        Py_VISIT(self->other);
                                    }
                                    return 0;
                                }

                                static PyObject* tc0_set_obj(TestCycle0Object* self, PyObject* arg) {
                                    self->other = Py_NewRef(arg);
                                    return Py_NewRef(Py_None);
                                }

                                static PyObject* tc0_get_obj(TestCycle0Object* self) {
                                    return Py_NewRef(self->other);
                                }

                                static PyObject* tc0_is_freed(PyObject* unused, PyObject* idx) {
                                    long l = PyLong_AsLong(idx);
                                    if (l < 0 || l >= N) {
                                        PyErr_Format(PyExc_ValueError, "invalid index; must be between 0 and %d", N);
                                        return NULL;
                                    }
                                    return PyBool_FromLong(freed[l]);
                                }
                                ''',
                                includes='#include <stdio.h>',
                                cmembers="""int idx;
                                            PyObject *other;""",
                                tp_init='(initproc)tc0_init',
                                tp_methods="""
                                {"set_obj", (PyCFunction)tc0_set_obj, METH_O, ""},
                                {"get_obj", (PyCFunction)tc0_get_obj, METH_NOARGS, ""},
                                {"is_freed", (PyCFunction)tc0_is_freed, METH_O | METH_CLASS, ""}
                                """,
                                tp_flags='Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC',
                                tp_traverse='(traverseproc)tc0_traverse',
                                tp_clear='(inquiry)tc0_clear',
                                tp_dealloc='(destructor)tc0_dealloc',
        )
        obj0 = TestCycle0(0)
        obj1 = TestCycle0(1)

        # establish cycle: obj0 -> obj1 -> obj0
        obj0.set_obj(obj1)
        obj1.set_obj(obj0)

        assert not TestCycle0.is_freed(0)
        assert not TestCycle0.is_freed(1)

        del obj1
        self._trigger_gc()
        assert not TestCycle0.is_freed(0)
        assert not TestCycle0.is_freed(1)

        del obj0
        self._trigger_gc()
        assert TestCycle0.is_freed(0)
        assert TestCycle0.is_freed(1)

        obj2 = TestCycle0(0)
        obj3 = TestCycle0(1)
        obj4 = TestCycle0(2)
        obj5 = TestCycle0(3)

        # establish cycle: obj2 -> obj3 -> l -> obj2
        obj2.set_obj(obj3)
        l = [obj2]
        obj3.set_obj(l)

        # establish cycle: obj4 -> obj5 -> l1 -> obj4
        obj4.set_obj(obj5)
        l1 = [obj4]
        obj5.set_obj(l1)

        assert not TestCycle0.is_freed(0)
        assert not TestCycle0.is_freed(1)
        assert not TestCycle0.is_freed(2)
        assert not TestCycle0.is_freed(3)
        del obj2, l, obj3
        del obj4, obj5
        self._trigger_gc()
        assert TestCycle0.is_freed(0)
        assert TestCycle0.is_freed(1)
        # because l1 is still alive
        assert not TestCycle0.is_freed(2)
        assert not TestCycle0.is_freed(3)

        rescued_obj4 = l1[0]
        del l1
        self._trigger_gc()
        # still reachable
        assert not TestCycle0.is_freed(2)
        assert not TestCycle0.is_freed(3)
        assert rescued_obj4.get_obj().get_obj()[0] is rescued_obj4

        del rescued_obj4

        # establish cycles: obj4 -> l2 -> obj5 -> l3 -> obj4 ;; l2 -> obj5 -> l3 -> l2
        obj4 = TestCycle0(4)
        obj5 = TestCycle0(5)
        l2 = [obj5]
        l3 = [obj4, l2]
        obj4.set_obj(l2)
        obj5.set_obj(l3)
        assert not TestCycle0.is_freed(4)
        assert not TestCycle0.is_freed(5)
        del obj4, obj5, l2, l3
        self._trigger_gc()
        assert TestCycle0.is_freed(2)
        assert TestCycle0.is_freed(3)
        assert TestCycle0.is_freed(4)
        assert TestCycle0.is_freed(5)


    def test_cycle_with_lists(self):
        TestCycle = CPyExtType("TestCycle",
                               '''
                               #define N 2
                               static int freed[N];

                               static void tc_dealloc(TestCycleObject* self) {
                                   freed[self->idx] = 1;
                                   PyObject_Free(self);
                               }

                               static int tc_check_index(long l) {
                                    if (l < 0 || l >= N) {
                                        PyErr_Format(PyExc_ValueError, "invalid index; must be between 0 and %d", N);
                                        return -1;
                                    }
                                    return 0;
                                }

                                static int tc_init(TestCycleObject* self, PyObject* args, PyObject *kwargs) {
                                    long l; 
                                    if (!PyArg_ParseTuple(args, "l", &l)) {
                                        return -1;
                                    }
                                    if (tc_check_index(l) < 0) {
                                        return -1;
                                    }
                                    self->idx = l;
                                    freed[l] = 0;
                                    return 0;
                                }

                               static PyObject* tc_set_list_item(PyObject* class, PyObject* arg) {
                                   long l = PyLong_AsLong(arg);
                                   if (tc_check_index(l) < 0) {
                                       return NULL;
                                   }
                                   PyObject *container0 = PyList_New(2);
                                   PyObject *container1 = PyList_New(1);
                                   TestCycleObject *obj = PyObject_New(TestCycleObject, (PyTypeObject *)class);
                                   obj->idx = l;
                                   freed[l] = 0;
                                   PyList_SET_ITEM(container0, 0, container1);
                                   PyList_SET_ITEM(container0, 1, obj);
                                   PyList_SET_ITEM(container1, 0, container0);
                                   return Py_BuildValue("(OO)", container0, container1);
                               }

                               static PyObject* tc_is_freed(PyObject* unused, PyObject* idx) {
                                   long l = PyLong_AsLong(idx);
                                   if (tc_check_index(l) < 0) {
                                       return NULL;
                                   }
                                   return PyBool_FromLong(freed[l]);
                               }
                               ''',
                               cmembers="int idx;",
                               tp_init='(initproc)tc_init',
                               tp_methods="""
                               {"set_list_item", (PyCFunction)tc_set_list_item, METH_O | METH_CLASS, ""},
                               {"is_freed", (PyCFunction)tc_is_freed, METH_O | METH_CLASS, ""}
                               """,
                               tp_flags='Py_TPFLAGS_DEFAULT',
                               tp_dealloc='(destructor)tc_dealloc',
                               )
        l0, l1 = TestCycle.set_list_item(0)

        ml0, ml1 = list(), list()
        ml0.append(ml1)
        ml0.append(TestCycle(1))
        ml1.append(ml0)

        assert not TestCycle.is_freed(0)
        assert not TestCycle.is_freed(1)
        assert l0[0] == l1
        assert l1[0] == l0
        assert ml0[0] == ml1
        assert ml1[0] == ml0

        del l0
        del l1
        del ml0
        del ml1
        self._trigger_gc()
        assert TestCycle.is_freed(0)
        assert TestCycle.is_freed(1)
