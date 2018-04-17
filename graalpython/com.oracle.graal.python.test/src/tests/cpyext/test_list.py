# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
import sys
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


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
    listObj = args[0]
    pos = args[1]
    newitem = args[2]
    if pos < 0:
        raise IndexError("list index out of range")
    listObj[pos] = newitem
    return 0


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


class TestPyList(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyList, self).compile_module(name)


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

    test_PyList_SetItem = CPyExtFunction(
        _wrap_list_fun(_reference_setitem),
        lambda: (
            ([1,2,3,4], 0, 0), 
            ([1,2,3,4], 3, 5), 
        ),
        resultspec="i",
        argspec='OnO',
        arguments=["PyObject* op", "Py_ssize_t size", "PyObject* newitem"],
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
