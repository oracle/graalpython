# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


def _reference_getslice(args):
    t = args[0]
    start = args[1]
    end = args[2]
    return t[start:end]


class MyStr(str):
    def __init__(self, s):
        self.s = s
    def __repr__(self):
        return self.s

class TestPyTuple(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyTuple, self).compile_module(name)

    # PyTuple_Size
    test_PyTuple_Size = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (
            (tuple(),), 
            ((1,2,3),), 
            (("a", "b"),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* tuple"],
    )

    # PyTuple_GetSlice
    test_PyTuple_GetSlice = CPyExtFunctionOutVars(
        _reference_getslice,
        lambda: (
            (tuple(),0,0), 
            ((1,2,3),0,2),
            ((4,5,6),1,2),
            ((7,8,9),2,2),
        ),
        resultspec="O",
        argspec='Onn',
        arguments=["PyObject* tuple", "Py_ssize_t start", "Py_ssize_t end"],
    )
    
    # PyTuple_Pack
    test_PyTuple_Pack = CPyExtFunctionOutVars(
        lambda vals: vals[1:],
        lambda: ((3, MyStr("hello"),MyStr("beautiful"),MyStr("world")), ),
        resultspec="O",
        argspec='nOOO',
        arguments=["Py_ssize_t n", "PyObject* arg0", "PyObject* arg1", "PyObject* arg2"],
    )
