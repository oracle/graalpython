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

import os
from unittest import skipIf
from . import CPyExtType

__dir__ = __file__.rpartition("/")[0]

GRAALPY = sys.implementation.name == 'graalpy'
GRAALPY_NATIVE = GRAALPY and __graalpython__.get_platform_id() == 'native'

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
        if GRAALPY_NATIVE:
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

ID_OBJ0 = 0
ID_OBJ1 = 1
ID_OBJ2 = 0
ID_OBJ3 = 1
ID_OBJ4 = 2
ID_OBJ5 = 3
ID_OBJ6 = 4
ID_OBJ7 = 5
ID_OBJ8 = 6
ID_OBJ9 = 7
ID_OBJ10 = 8
ID_OBJ11 = 9
ID_OBJ12 = 10
ID_OBJ13 = 11
ID_OBJ14 = 12

# don't rely on deterministic Java GC behavior by default on GraalPy
RELY_ON_GC = os.environ.get("RELY_ON_GC", not GRAALPY)

if GRAALPY_NATIVE and RELY_ON_GC:
    import warnings
    warnings.warn("Relying on deterministic Java GC behavior. "
                  "Tests may fail if the Java GC doesn't run at a certain program point or doesn't collect objects "
                  "as we expect.")

if GRAALPY_NATIVE:
    get_handle_table_id = __graalpython__.get_handle_table_id
    def assert_is_strong(x): assert __graalpython__.is_strong_handle_table_ref(x)
    def assert_is_weak(x): assert not __graalpython__.is_strong_handle_table_ref(x)
else:
    # just that the test is compatible with CPython
    def get_handle_table_id(object): return -1
    def assert_is_strong(x): pass
    def assert_is_weak(x): pass

class TestGCRefCycles:
    def _trigger_gc(self):
        gc.collect()
        for i in range(4 if GRAALPY_NATIVE and RELY_ON_GC else 1):
            time.sleep(0.25)
            gc.collect()

    @skipIf(GRAALPY and not GRAALPY_NATIVE, "Python GC only used in native mode")
    def test_cycle_with_native_objects(self):
        TestCycle0 = CPyExtType("TestCycle0",
                                '''
                                #define N 16
                                static int freed[N];
                                static PyObject *global_objs[N];

                                #ifdef DEBUG
                                #define log printf
                                #else
                                #define log(...)
                                #endif

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
                                    log("clear of %d called\\n", self->idx);
                                    Py_CLEAR(self->other);
                                }

                                static void tc0_dealloc(TestCycle0Object* self) {
                                    log("dealloc of %d called\\n", self->idx);
                                    PyObject_GC_UnTrack(self);
                                    tc0_clear(self);
                                    freed[self->idx] = 1;
                                    PyObject_GC_Del(self);
                                }

                                static int tc0_traverse(TestCycle0Object* self, visitproc visit, void* arg) {
                                    log("traverse of %d called -- refcnt = %zd\\n", self->idx, Py_REFCNT(self));
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

                                static PyObject* tc0_set_global_obj(PyObject* unused, PyObject* args) {
                                    size_t i;
                                    PyObject *arg;
                                    if (!PyArg_ParseTuple(args, "nO", &i, &arg)) {
                                        return NULL;
                                    }
                                    if (i < 0 || i >= N) {
                                        PyErr_Format(PyExc_ValueError, "invalid index; must be between 0 and %d", N);
                                        return NULL;
                                    }
                                    Py_XSETREF(global_objs[i], Py_NewRef(arg));
                                    return Py_NewRef(Py_None);
                                }
                                ''',
                                includes='#include <stdio.h>',
                                cmembers="""int idx;
                                            PyObject *other;""",
                                tp_init='(initproc)tc0_init',
                                tp_methods="""
                                {"set_obj", (PyCFunction)tc0_set_obj, METH_O, ""},
                                {"set_global_obj", (PyCFunction)tc0_set_global_obj, METH_VARARGS | METH_CLASS, ""},
                                {"get_obj", (PyCFunction)tc0_get_obj, METH_NOARGS, ""},
                                {"is_freed", (PyCFunction)tc0_is_freed, METH_O | METH_CLASS, ""}
                                """,
                                tp_flags='Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC',
                                tp_traverse='(traverseproc)tc0_traverse',
                                tp_clear='(inquiry)tc0_clear',
                                tp_dealloc='(destructor)tc0_dealloc',
        )

        if RELY_ON_GC:
            def assert_is_alive(id):
                assert not TestCycle0.is_freed(id)
            def assert_is_freed(id):
                assert TestCycle0.is_freed(id)
        else:
            def assert_is_alive(id): pass
            def assert_is_freed(id): pass

        obj0 = TestCycle0(ID_OBJ0)
        obj1 = TestCycle0(ID_OBJ1)

        # establish cycle: obj0 -> obj1 -> obj0
        obj0.set_obj(obj1)
        obj1.set_obj(obj0)

        assert_is_alive(ID_OBJ0)
        assert_is_alive(ID_OBJ1)

        del obj1
        ####################################### GC #######################################
        self._trigger_gc()
        ##################################################################################
        assert_is_alive(ID_OBJ0)
        assert_is_alive(ID_OBJ1)

        del obj0
        ####################################### GC #######################################
        self._trigger_gc()
        ##################################################################################
        assert_is_freed(ID_OBJ0)
        assert_is_freed(ID_OBJ1)

        obj2 = TestCycle0(ID_OBJ2)
        obj3 = TestCycle0(ID_OBJ3)
        obj4 = TestCycle0(ID_OBJ4)
        obj5 = TestCycle0(ID_OBJ5)
        obj6 = TestCycle0(ID_OBJ6)
        obj7 = TestCycle0(ID_OBJ7)
        obj8 = TestCycle0(ID_OBJ8)
        obj9 = TestCycle0(ID_OBJ9)
        obj10 = TestCycle0(ID_OBJ10)
        obj11 = TestCycle0(ID_OBJ11)
        obj14 = TestCycle0(ID_OBJ14)

        # Legend
        # '=>'
        #   A strong reference without handle table indirection. Possible cases:
        #   1. native_object => native_object
        #   2. managed_object => managed_object
        #   2. managed_object => native_object
        #
        # '=ht=>'
        #   A strong reference through the handle table. So, this can only be a reference from a native to a managed
        #   object.
        #
        # '=ht->'
        #   Similar as above (a native object references a managed object) but the handle table only has a weak
        #   Java reference to the managed object. From the native object's point of view, the reference is still strong
        #   and it will do a decref on clear/dealloc.
        #
        #
        # The phases "update_refs", "subtract_refs", and "move_unreachable" refer to the corresponding C functions
        # (see C function "deduce_unreachable").
        #
        # The reference count values in the comments are the 'gc_refs' (which is stored in 'PyGC_Head._gc_prev' during a
        # GC run) and not 'ob_refcnt'.
        # The values are the expected value *AFTER* the corresponding phase.

        # establish cycle:  obj2 => obj3 =ht=> l => obj2
        # update_refs:       10      1         11
        # subtract_refs:     10      0         10
        # move_unreachable:  10      0         10
        # update_refs:       10      11        11
        # subtract_refs:     10      10        10
        # commit_weak_cand: obj2 => obj3 =ht-> l => obj2
        obj2.set_obj(obj3)
        l = [obj2]
        obj3.set_obj(l)
        htid_l = get_handle_table_id(l)

        # establish cycle:  obj4 => obj5 =ht=> l1 => obj6 => obj4
        # update_refs:       1       1         10     11
        # subtract_refs:     0       0         10     11
        # move_unreachable:  1       1         10     11
        # commit_weak_cand: obj4 => obj5 =ht-> l1 => obj6 => obj4

        # establish cycle:  obj4 => obj5 =ht=> l1 => obj4
        # update_refs:       10      1         11
        # subtract_refs:     10      0         10
        # move_unreachable:  10      1         10
        # update_refs:       10      11        11
        # subtract_refs:     10      10        10
        # commit_weak_cand: obj4 => obj5 =ht-> l1 => obj4
        obj4.set_obj(obj5)
        l1 = [obj4]
        obj5.set_obj(l1)
        htid_l1 = get_handle_table_id(l1)

        # establish cycle: obj6 => obj7 =ht=> d0 => obj6
        obj6.set_obj(obj7)
        d0 = {0: obj6}
        obj7.set_obj(d0)
        htid_d0 = get_handle_table_id(d0)

        # J-> obj9 -> obj8 -> ["hello"]
        obj8.set_obj(["hello"])
        obj9.set_obj(obj8)
        del obj8

        #                   N => obj10 =ht=> l2
        # update_refs:             1         11
        # subtract_refs:           1         10
        # move_unreachable:        1         10
        # update_refs:             1         11
        # subtract_refs:           1         10
        # commit_weak_cand: N => obj10 =ht=> l2
        l2 = ["hello"]
        obj10.set_obj(l2)
        TestCycle0.set_global_obj(0, obj10)
        htid_l2 = get_handle_table_id(l2)
        del obj10, l2

        #                   J/N => obj11 =ht=> l3
        # update_refs:               11        11
        # subtract_refs:             11        10
        # move_unreachable:          11        10
        # update_refs:               11        11
        # subtract_refs:             11        10
        # commit_weak_cand: J/N => obj11 =ht=> l3
        l3 = ["hello"]
        obj11.set_obj(l3)
        TestCycle0.set_global_obj(1, obj11)
        htid_l3 = get_handle_table_id(l3)
        del l3
        # difference to previous situation: obj11 is still reachable from Java

        #                   J => obj14 =ht=> l3
        # update_refs:             10        11
        # subtract_refs:           10        10
        # move_unreachable:        10        10
        # update_refs:             10        11
        # subtract_refs:           10        10
        # commit_weak_cand: J/N => obj11 =ht=> l3
        l4 = ["world"]
        obj14.set_obj(l4)
        htid_l4 = get_handle_table_id(l4)
        del l4

        # everything should still be alive
        assert_is_alive(ID_OBJ2)
        assert_is_alive(ID_OBJ3)
        assert_is_alive(ID_OBJ4)
        assert_is_alive(ID_OBJ5)
        assert_is_alive(ID_OBJ6)
        assert_is_alive(ID_OBJ7)
        assert_is_alive(ID_OBJ8)
        assert_is_alive(ID_OBJ9)
        assert_is_alive(ID_OBJ10)
        assert_is_alive(ID_OBJ11)
        assert_is_alive(ID_OBJ14)
        assert_is_strong(htid_l)
        assert_is_strong(htid_l1)
        assert_is_strong(htid_l2)
        assert_is_strong(htid_l3)
        assert_is_strong(htid_l4)
        assert_is_strong(htid_d0)

        del obj2, l, obj3
        del obj4, obj5
        del obj6, d0, obj7

        ####################################### GC #######################################
        self._trigger_gc()
        ##################################################################################

        # Delete Java ref after GC. This will provoke the situation where 'PythonAbstractNativeObject' of obj11 will
        # die after references where potentially replicated. This tests if dangling pointers appear for the managed
        # referent.
        del obj11

        assert_is_freed(ID_OBJ2)
        assert_is_freed(ID_OBJ3)
        assert_is_freed(ID_OBJ6)
        assert_is_freed(ID_OBJ7)
        # because l1 is still alive
        assert_is_alive(ID_OBJ4)
        assert_is_alive(ID_OBJ5)
        assert_is_alive(ID_OBJ8)
        assert_is_alive(ID_OBJ9)
        assert_is_alive(ID_OBJ10)
        assert_is_alive(ID_OBJ14)
        assert_is_strong(htid_l2)
        assert_is_strong(htid_l3)
        assert_is_weak(htid_l)
        assert_is_weak(htid_l1)
        assert_is_weak(htid_l4)
        assert_is_weak(htid_d0)

        rescued_obj4 = l1[0]
        del l1

        TestCycle0.set_global_obj(2, obj14)
        del obj14
        ####################################### GC #######################################
        self._trigger_gc()
        ##################################################################################
        # still reachable
        assert_is_alive(ID_OBJ4)
        assert_is_alive(ID_OBJ5)
        assert_is_alive(ID_OBJ14)
        assert rescued_obj4.get_obj().get_obj()[0] is rescued_obj4
        assert_is_strong(htid_l4)

        del rescued_obj4

        # establish cycles: obj12 =ht=> l2 => obj13 =ht=> l3 => obj12 ;; l2 -> obj13 =ht=> l3 => l2
        # update_refs:       10         11     10         11
        # subtract_refs:     10         10     10         10
        # move_unreachable:  10         10     10         10
        # commit_weak_cand: obj12 =ht-> l2 => obj13 =ht-> l3 => obj12 ;; l2 -> obj13 =ht-> l3 => l2
        obj12 = TestCycle0(ID_OBJ12)
        obj13 = TestCycle0(ID_OBJ13)
        l2 = [obj13]
        l3 = [obj12, l2]
        obj12.set_obj(l2)
        obj13.set_obj(l3)
        htid_l2 = get_handle_table_id(l2)
        htid_l3 = get_handle_table_id(l3)

        assert_is_alive(ID_OBJ12)
        assert_is_alive(ID_OBJ13)
        assert_is_strong(htid_l2)
        assert_is_strong(htid_l3)

        del obj12, obj13, l2, l3

        ####################################### GC #######################################
        self._trigger_gc()
        ##################################################################################

        assert_is_freed(ID_OBJ4)
        assert_is_freed(ID_OBJ5)
        assert_is_freed(ID_OBJ12)
        assert_is_freed(ID_OBJ13)
        assert_is_weak(htid_l2)
        assert_is_weak(htid_l3)


    @skipIf(GRAALPY and not GRAALPY_NATIVE, "Python GC only used in native mode")
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
        if RELY_ON_GC:
            def assert_is_alive(id):
                assert not TestCycle.is_freed(id)
            def assert_is_freed(id):
                assert TestCycle.is_freed(id)
        else:
            def assert_is_alive(id): pass
            def assert_is_freed(id): pass

        l0, l1 = TestCycle.set_list_item(0)
        htid_l0 = get_handle_table_id(l0)
        htid_l1 = get_handle_table_id(l1)

        ml0, ml1 = list(), list()
        ml0.append(ml1)
        ml0.append(TestCycle(1))
        ml1.append(ml0)

        assert_is_alive(0)
        assert_is_alive(1)
        assert_is_strong(htid_l0)
        assert_is_strong(htid_l1)
        assert l0[0] is l1
        assert l1[0] is l0
        assert ml0[0] is ml1
        assert ml1[0] is ml0

        del l0, l1, ml0, ml1
        self._trigger_gc()
        assert_is_freed(0)
        assert_is_freed(1)
        assert_is_weak(htid_l0)
        assert_is_weak(htid_l1)

    def test_module_globals(self):
        self._trigger_gc()
        GCTestModuleGlobal = CPyExtType("GCTestModuleGlobal", 
                        '''
                        struct struct_GC_Test_C
                        {
                            PyObject_HEAD
                            PyObject *_test_list_c;
                        };

                        struct struct_GC_Test_B
                        {
                            struct struct_GC_Test_C base;
                        };

                        struct struct_GC_Test_A
                        {
                            PyObject_HEAD
                            struct struct_GC_Test_B *_test_b;
                        };

                        struct struct_GC_Test_G
                        {
                            PyObject_HEAD
                            struct struct_GC_Test_A *_test_a;
                        };

                        static struct struct_GC_Test_G *__GC_Test_Global__ = 0;
                        static PyTypeObject *pytype_GC_Test_G = 0;
                        static PyTypeObject *pytype_GC_Test_A = 0;
                        static PyTypeObject *pytype_GC_Test_B = 0;
                        static PyTypeObject *pytype_GC_Test_C = 0;

                        static PyObject *tp_new_GC_Test_G(PyTypeObject *t, PyObject *a, PyObject *k) {
                            struct struct_GC_Test_G *p;
                            PyObject *o = (*t->tp_alloc)(t, 0);
                            p = ((struct struct_GC_Test_G *)o);
                            p->_test_a = ((struct struct_GC_Test_A *)Py_None);
                            Py_INCREF(Py_None);
                            return o;
                        }

                        static int tp_traverse_GC_Test_G(PyObject *o, visitproc v, void *a) {
                            struct struct_GC_Test_G *p = (struct struct_GC_Test_G *)o;
                            if (p->_test_a)
                                return (*v)(((PyObject *)p->_test_a), a);
                            return 0;
                        }

                        static PyObject *tp_new_GC_Test_A(PyTypeObject *t, PyObject *a, PyObject *k) {
                            PyObject *o = (*t->tp_alloc)(t, 0);
                            PyObject *t1 = PyObject_CallNoArgs(((PyObject *)pytype_GC_Test_B));
                            ((struct struct_GC_Test_A *)o)->_test_b = ((struct struct_GC_Test_B *)t1);
                            return o;
                        }

                        static int tp_traverse_GC_Test_A(PyObject *o, visitproc v, void *a) {
                            struct struct_GC_Test_A *p = (struct struct_GC_Test_A *)o;
                            if (p->_test_b)
                                return (*v)(((PyObject *)p->_test_b), a);
                            return 0;
                        }

                        static PyObject *tp_new_GC_Test_C(PyTypeObject *t, PyObject *a, PyObject *k) {
                            struct struct_GC_Test_C *p;
                            PyObject *o = (*t->tp_alloc)(t, 0);
                            p = ((struct struct_GC_Test_C *)o);
                            p->_test_list_c = ((PyObject *)Py_None);
                            Py_INCREF(Py_None);
                            return o;
                        }

                        static int tp_traverse_GC_Test_C(PyObject *o, visitproc v, void *a) {
                            struct struct_GC_Test_C *p = (struct struct_GC_Test_C *)o;
                            if (p->_test_list_c)
                                return (*v)(p->_test_list_c, a);
                            return 0;
                        }

                        static PyObject *tp_new_GC_Test_B(PyTypeObject *t, PyObject *a, PyObject *k) {
                            struct struct_GC_Test_B *p;
                            PyObject *o = tp_new_GC_Test_C(t, a, k);
                            p = ((struct struct_GC_Test_B *)o);
                            return o;
                        }

                        static struct struct_GC_Test_A *GC_Test_G_getGCTestA(struct struct_GC_Test_G *self) {
                            PyObject *t3 = NULL;
                            if (((PyObject *)self->_test_a) == Py_None)
                            {
                                t3 = PyObject_CallNoArgs(((PyObject *)pytype_GC_Test_A));
                                Py_DECREF(((PyObject *)self->_test_a));
                                self->_test_a = ((struct struct_GC_Test_A *)t3);
                            }

                            Py_INCREF(((PyObject *)self->_test_a));
                            return self->_test_a;
                        }

                        static PyObject *GC_Test_B_clear(struct struct_GC_Test_B *self) {
                            PyObject *py_slice = PySlice_New(Py_None, Py_None, Py_None);
                            PyObject_DelItem(self->base._test_list_c, py_slice);
                            Py_INCREF(Py_None);
                            return Py_None;
                        }

                        static int GC_Test_A_prepare(struct struct_GC_Test_A *self) {
                            PyObject *t1 = GC_Test_B_clear(self->_test_b);
                            Py_DECREF(t1);
                            return 0;
                        }

                        static int GC_Test_A_cleanup(struct struct_GC_Test_A *self) {
                            PyObject *t1 = GC_Test_B_clear(self->_test_b);
                            Py_DECREF(t1);
                            return 0;
                        }

                        static int GC_Test_C__init__(PyObject *self, PyObject *__pyx_args, PyObject *__pyx_kwds) {
                            PyObject *list_c = PyTuple_GET_ITEM(__pyx_args, 0);
                            Py_INCREF(list_c);
                            Py_DECREF(((struct struct_GC_Test_C *)self)->_test_list_c);
                            printf("list_c: %p\\n", list_c);
                            ((struct struct_GC_Test_C *)self)->_test_list_c = list_c;
                            return 0;
                        }

                        static int GC_Test_B__init__(PyObject *self, PyObject *__pyx_args, PyObject *__pyx_kwds) {
                            PyObject *t1 = NULL;
                            PyObject *t2 = PyObject_GetAttrString((PyObject *)pytype_GC_Test_C, "__init__");
                            PyObject *t3 = PyList_New(0);
                            PyObject *t6 = PyTuple_New(2);
                            Py_INCREF(self);
                            PyTuple_SET_ITEM(t6, 0, self);
                            PyTuple_SET_ITEM(t6, 1, t3);
                            t1 = PyObject_Call(t2, t6, NULL);
                            Py_DECREF(t6);
                            Py_DECREF(t2);
                            Py_DECREF(t1);
                            return 0;
                        }


                        static PyObject *GC_Test_Global(PyObject *self, PyObject *unused) {
                            struct struct_GC_Test_A *__pyx_v_A = 0;
                            __pyx_v_A = GC_Test_G_getGCTestA(__GC_Test_Global__);
                            GC_Test_A_prepare(__pyx_v_A);
                            GC_Test_A_cleanup(__pyx_v_A);
                            Py_INCREF(Py_None);
                            Py_XDECREF((PyObject *)__pyx_v_A);
                            return Py_None;
                        }

                        PyTypeObject spec_GC_Test_C = {
                            PyVarObject_HEAD_INIT(NULL, 0)
                            .tp_name = "GC_Test_C",
                            .tp_basicsize = sizeof(struct struct_GC_Test_C),
                            .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_GC,
                            .tp_traverse = tp_traverse_GC_Test_C,
                            .tp_init = GC_Test_C__init__,
                            .tp_new = tp_new_GC_Test_C,
                        };

                        PyTypeObject spec_GC_Test_B = {
                            PyVarObject_HEAD_INIT(NULL, 0)
                            .tp_name = "GC_Test_B",
                            .tp_basicsize = sizeof(struct struct_GC_Test_B),
                            .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_GC,
                            .tp_traverse = tp_traverse_GC_Test_C,
                            .tp_init = GC_Test_B__init__,
                            .tp_new = tp_new_GC_Test_B,
                            .tp_base = &spec_GC_Test_C,
                        };

                        PyTypeObject spec_GC_Test_A = {
                            PyVarObject_HEAD_INIT(NULL, 0)
                            .tp_name = "GC_Test_A",
                            .tp_basicsize = sizeof(struct struct_GC_Test_A),
                            .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_GC,
                            .tp_traverse = tp_traverse_GC_Test_A,
                            .tp_new = tp_new_GC_Test_A,
                        };

                        PyTypeObject spec_GC_Test_G = {
                            PyVarObject_HEAD_INIT(NULL, 0)
                            .tp_name = "GC_Test_G",
                            .tp_basicsize = sizeof(struct struct_GC_Test_G),
                            .tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_HAVE_GC,
                            .tp_traverse = tp_traverse_GC_Test_G,
                            .tp_new = tp_new_GC_Test_G,
                        };

                        ''',
                        tp_methods='''{"GC_Test_Global", (PyCFunction)GC_Test_Global, METH_NOARGS | METH_CLASS, ""}''',
                        ready_code='''

                            if (PyType_Ready(&spec_GC_Test_G) < 0)
                                return NULL;
                            pytype_GC_Test_G = &spec_GC_Test_G;

                            if (PyType_Ready(&spec_GC_Test_A) < 0)
                                return NULL;
                            pytype_GC_Test_A = &spec_GC_Test_A;

                            if (PyType_Ready(&spec_GC_Test_C) < 0)
                                return NULL;
                            pytype_GC_Test_C = &spec_GC_Test_C;

                            if (PyType_Ready(&spec_GC_Test_B) < 0)
                                return NULL;
                            pytype_GC_Test_B = &spec_GC_Test_B;

                            __GC_Test_Global__ = (struct struct_GC_Test_G *)PyObject_CallNoArgs(((PyObject *)pytype_GC_Test_G));
                            ''',
                        )
        
        GCTestModuleGlobal.GC_Test_Global()
        ####################################### GC #######################################
        self._trigger_gc()
        ##################################################################################
        GCTestModuleGlobal.GC_Test_Global()
        ####################################### GC #######################################
        self._trigger_gc()
        ##################################################################################
        GCTestModuleGlobal.GC_Test_Global()
        ####################################### GC #######################################
        self._trigger_gc()
        ##################################################################################
        GCTestModuleGlobal.GC_Test_Global()
        ####################################### GC #######################################
        self._trigger_gc()
        ##################################################################################
