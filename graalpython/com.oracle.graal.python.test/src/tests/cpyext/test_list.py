# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtTestCase, CPyExtFunction, compile_module_from_string, unhandled_error_compare


def _reference_new_list(args):
    n = args[0]
    if n < 0:
        raise SystemError
    return [None for x in range(n)]


def _reference_getitem(args):
    listObj = args[0]
    pos = args[1]
    if pos < 0:
        raise IndexError("list index out of range")
    return listObj[pos]


def _reference_setitem(args):
    capacity = args[0]
    pos = args[1]
    newitem = args[2]
    if pos < 0:
        raise IndexError("list index out of range")
    listObj = [None] * capacity
    listObj[pos] = newitem
    return listObj


def _reference_setslice(args):
    if not isinstance(args[0], list):
        raise SystemError
    args[0][args[1]:args[2]] = args[3]
    return 0;


def _reference_reverse(args):
    args[0].reverse()
    return args[0]


def _reference_SET_ITEM(args):
    listObj = args[0]
    pos = args[1]
    newitem = args[2]
    listObj[pos] = newitem
    return listObj


def _reference_append(args):
    listObj = args[0]
    newitem = args[1]
    try:
        listObj.append(newitem)
        return 0
    except:
        if sys.version_info.minor >= 6:
            raise SystemError
        return -1


def _wrap_list_fun(fun, since=0, default=None):
    def wrapped_fun(args):
        if not isinstance(args[0], list):
            if sys.version_info.minor >= since:
                raise SystemError("expected list type")
            else:
                return default
        return fun(args)
    return wrapped_fun


class DummyClass:
    def __eq__(self, other):
        return isinstance(other, DummyClass)


class DummyListSubclass(list):
    pass


class TestPyList(CPyExtTestCase):

    def test_clear_native_storage_gc(self):
        module = compile_module_from_string(
            """
            #define PY_SSIZE_T_CLEAN
            #include <Python.h>

            static PyObject* clear_native_list_storage(PyObject* self, PyObject* list) {
                if (!PyList_Check(list) || PyList_GET_SIZE(list) == 0) {
                    PyErr_SetString(PyExc_ValueError, "expected non-empty list");
                    return NULL;
                }
                // Force storage to native
                PySequence_Fast_ITEMS(list);
                if (Py_TYPE(list)->tp_clear((PyObject *) list) < 0) {
                    return NULL;
                }
                Py_RETURN_NONE;
            }

            static PyMethodDef methods[] = {
                {"clear_native_list_storage", (PyCFunction) clear_native_list_storage, METH_O, NULL},
                {NULL, NULL, 0, NULL}
            };

            static struct PyModuleDef module = {
                PyModuleDef_HEAD_INIT, "test_list_native_storage_gc", NULL, -1, methods
            };

            PyMODINIT_FUNC PyInit_test_list_native_storage_gc(void) {
                return PyModule_Create(&module);
            }
            """,
            "test_list_native_storage_gc",
        )

        items = [object()]
        module.clear_native_list_storage(items)
        del items
        for _ in range(5):
            gc.collect()

    test_PyList_New = CPyExtFunction(
        _reference_new_list,
        lambda: (
            (0,),
            (-1,)
        ),
        resultspec="O",
        argspec='n',
        arguments=["Py_ssize_t size"],
        cmpfunc=unhandled_error_compare
    )

    test_PyList_GetItem = CPyExtFunction(
        _wrap_list_fun(_reference_getitem),
        lambda: (
            ([1,2,3,4], 0),
            ([1,2,3,4], 3),
            #([None], 0),
            ([], 3),
            ([1,2,3,4], -1),
            ((1,2,3,4), 0),
            (DummyClass(), 0),
        ),
        resultspec="O",
        argspec='On',
        arguments=["PyObject* op", "Py_ssize_t size"],
        cmpfunc=unhandled_error_compare
    )

    test_PyList_GET_ITEM = CPyExtFunction(
        _wrap_list_fun(_reference_getitem),
        lambda: (
            ([1,2,3,4], 0),
            ([1,2,3,4], 3),
            ([None], 0),
        ),
        resultspec="O",
        argspec='On',
        arguments=["PyObject* op", "Py_ssize_t size"],
        cmpfunc=unhandled_error_compare
    )

    test_PyList_SetItem = CPyExtFunction(
        _reference_setitem,
        lambda: (
            (4, 0, 0, False),
            (4, 3, 5, False),
            (4, 0, 0, True),
            (4, 3, 5, True),
        ),
        code='''PyObject* wrap_PyList_SetItem(Py_ssize_t capacity, Py_ssize_t idx, PyObject* new_item, int init_with_none) {
            PyObject *newList = PyList_New(capacity);
            Py_ssize_t i;
            for (i = 0; i < capacity; i++) {
                if (init_with_none || i != idx) {
                    Py_INCREF(Py_None);
                    PyList_SetItem(newList, i, Py_None);
                }
            }
            Py_INCREF(new_item);
            PyList_SetItem(newList, idx, new_item);
            return newList;
        }
        ''',
        resultspec="O",
        argspec='nnOp',
        arguments=["Py_ssize_t capacity", "Py_ssize_t size", "PyObject* new_item", "int init_with_none"],
        callfunction="wrap_PyList_SetItem",
        cmpfunc=unhandled_error_compare
    )

    test_native_list_setitem_decrefs_none = CPyExtFunction(
        lambda args: [args[0]],
        lambda: (
            ("new item",),
        ),
        code='''PyObject* wrap_native_list_setitem_decrefs_none(PyObject* new_item) {
            PyObject *list = PyList_New(1);
            if (list == NULL) {
                return NULL;
            }
            Py_INCREF(Py_None);
            PyList_SET_ITEM(list, 0, Py_None);
            if (PySequence_SetItem(list, 0, new_item) < 0) {
                Py_DECREF(list);
                return NULL;
            }
            return list;
        }
        ''',
        resultspec="O",
        argspec='O',
        arguments=["PyObject* new_item"],
        callfunction="wrap_native_list_setitem_decrefs_none",
        cmpfunc=unhandled_error_compare
    )

    test_PyList_SET_ITEM = CPyExtFunction(
        _wrap_list_fun(_reference_SET_ITEM),
        lambda: (
            ([1,2,3,4], 0, _reference_SET_ITEM),
            ([1,2,3,4], 3, _reference_SET_ITEM),
            ([None], 0, 0),
        ),
        code='''PyObject* wrap_PyList_SET_ITEM(PyObject* op, Py_ssize_t idx, PyObject* newitem) {
            Py_INCREF(newitem);
            PyList_SET_ITEM(op, idx, newitem);
            int refcnt1 = Py_REFCNT(newitem);
            PyList_SET_ITEM(op, idx, Py_None);
            int refcnt2 = Py_REFCNT(newitem);
            PyList_SET_ITEM(op, idx, newitem);
            int refcnt3 = Py_REFCNT(newitem);
            // Important! PyList_SET_ITEM does not change the refcnts,
            // but PyList_SetItem decrefs the element that was
            // previously at idx. Let's make sure that semantic
            // difference is observed
            if (refcnt1 != refcnt2 || refcnt2 != refcnt3) {
                return Py_BuildValue("iii", refcnt1, refcnt2, refcnt3);
            } else {
                return op;
            }
        }
        ''',
        resultspec="O",
        argspec='OnO',
        arguments=["PyObject* op", "Py_ssize_t idx", "PyObject* newitem"],
        callfunction="wrap_PyList_SET_ITEM",
        cmpfunc=unhandled_error_compare
    )

    test_PyList_Append = CPyExtFunction(
        _reference_append,
        lambda: (
            ([], 0),
            ([], "first"),
            #([], None),
            ([1], 2),
            ([1], "first"),
            #([1], None),
            ((1,), "first"),
            (DummyClass(), "first"),
        ),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* op", "PyObject* newitem"],
        cmpfunc=unhandled_error_compare
    )
    test_PyList_AsTuple = CPyExtFunction(
        _wrap_list_fun(lambda args: tuple(args[0])),
        lambda: (
            ([],),
            ([1,2,3,4],),
            (["first", "second", "third"],),
            (["mixed", 1, DummyClass()],),
            (("mixed", 1, DummyClass()),),
            ((None,),),
            (tuple(),),
            (DummyClass(),),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* op"],
        cmpfunc=unhandled_error_compare
    )

    test_PyList_GetSlice = CPyExtFunction(
        _wrap_list_fun(lambda args: args[0][args[1]:args[2]]),
        lambda: (
            ([1,2,3,4],0,4),
            ([1,2,3,4],0,1),
            ([1,2,3,4],3,4),
            ([1,2,3,4],0,0),
            ([],1,2),
            ([1,2,3,4],10,20),
            ((1,2,3,4),10,20),
            (DummyClass(),10,20),
        ),
        resultspec="O",
        argspec='Onn',
        arguments=["PyObject* op", "Py_ssize_t ilow", "Py_ssize_t ihigh"],
        cmpfunc=unhandled_error_compare
    )

    test_PyList_SetSlice = CPyExtFunction(
        _reference_setslice,
        lambda: (
            ([1,2,3,4],0,4,[5,6,7,8]),
            ([],1,2, [5,6]),
            ([1,2,3,4],10,20,[5,6,7,8]),
            (DummyClass(),10,20, [1]),
        ),
        resultspec="i",
        argspec='OnnO',
        arguments=["PyObject* op", "Py_ssize_t ilow", "Py_ssize_t ihigh", "PyObject* v"],
        cmpfunc=unhandled_error_compare
    )

    test_PyList_Size = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            ([1,2,3,4],),
            ([None],),
            ([],),
            ([1,2,3,4],),
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* op"],
        cmpfunc=unhandled_error_compare
    )

    test_PyList_GET_SIZE = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            ([1,2,3,4],),
            ([None],),
            ([],),
            ([1,2,3,4],),
            # no type checking, must not use non-list objects
        ),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* op"],
        cmpfunc=unhandled_error_compare
    )

    test_PyList_Check = CPyExtFunction(
        lambda args: isinstance(args[0], list),
        lambda: (
            ([1,2,3,4],),
            ([None],),
            ([],),
            (list(),),
            (dict(),),
            (tuple(),),
            (DummyListSubclass(),),
            (DummyClass(),),
            (1,),
            (1.0,),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyList_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is list,
        lambda: (
            ([1,2,3,4],),
            ([None],),
            ([],),
            (list(),),
            (dict(),),
            (tuple(),),
            (DummyListSubclass(),),
            (DummyClass(),),
            (1,),
            (1.0,),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyList_Reverse = CPyExtFunction(
        _reference_reverse,
        lambda: (
            ([],),
            ([1, 2, 3],),
            ([1, "a", 0.1],),
        ),
        code='''PyObject* wrap_PyList_Reverse(PyObject* list) {
            if (PyList_Reverse(list)) {
                return NULL;
            }
            Py_INCREF(list);
            return list;
        }
        ''',
        resultspec="O",
        argspec='O',
        arguments=["PyObject* list"],
        callfunction="wrap_PyList_Reverse",
        cmpfunc=unhandled_error_compare
    )
