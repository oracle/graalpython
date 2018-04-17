# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
import sys
from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]



def _reference_get_item(args):
    try:
        d = args[0]
        return d[args[1]]
    except:
        raise SystemError


def _reference_set_item(args):
    try:
        d = args[0]
        d[args[1]] = args[2]
        return 0
    except:
        raise SystemError


def _reference_del_item(args):
    try:
        d = args[0]
        del d[args[1]]
        return 0
    except:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return -1


def _reference_next(args):
    try:
        d = args[0]
        i = 0
        for k in d:
            if i == args[1]:
                return (1, k, d[k])
            i = i + 1
        return (0, None, None)
    except:
        return (0, None, None)


def _reference_copy(args):
    if not isinstance(args[0], dict):
        raise SystemError
    return args[0].copy()


class TestPyDict(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyDict, self).compile_module(name)

    # PyDict_SetItem
    test_PyDict_SetItem = CPyExtFunction(
        _reference_set_item,
        lambda: (({}, "a", "hello"), ({'a': "hello"}, "b", "world")),
        resultspec="i",
        argspec='OOO',
        arguments=("PyObject* dict", "PyObject* key", "PyObject* val"),
    )

    # PyDict_GetItem
    test_PyDict_GetItem = CPyExtFunction(
        _reference_get_item,
        lambda: (({}, "a", "dflt"), ({'a': "hello"}, "a", "dflt"), ({'a': "hello"}, "b", "dflt")),
        code='''PyObject* wrap_PyDict_GetItem(PyObject* dict, PyObject* key, PyObject* defaultVal) {
            PyObject* result = PyDict_GetItem(dict, key);
            return result; 
        }''',
        resultspec="O",
        argspec='OOO',
        arguments=("PyObject* dict", "PyObject* key", "PyObject* defaultVal"),
        callfunction="wrap_PyDict_GetItem",
        cmpfunc=unhandled_error_compare
    )

    # PyDict_DelItem
    test_PyDict_DelItem = CPyExtFunction(
        _reference_del_item,
        lambda: (({}, "a"), ({'a': "hello"}, "a"), ({'a': "hello"}, "b")),
        resultspec="i",
        argspec='OO',
        arguments=("PyObject* dict", "PyObject* key"),
        cmpfunc=unhandled_error_compare
    )

    # PyDict_SetItemString
    test_PyDict_SetItemString = CPyExtFunction(
        _reference_set_item,
        lambda: (({}, "a", "hello"), ({'a': "hello"}, "b", "world")),
        resultspec="i",
        argspec='OsO',
        arguments=("PyObject* dict", "char* key", "PyObject* val"),
    )

    # PyDict_GetItemString
    test_PyDict_GetItemString = CPyExtFunctionOutVars(
        _reference_get_item,
        lambda: (({}, "a", "dflt"), ({'a': "hello"}, "a", "dflt"), ({'a': "hello"}, "b", "dflt")),
        code='''PyObject* wrap_PyDict_GetItemString(PyObject* dict, char* key, PyObject* defaultValue) {
            PyObject* result = PyDict_GetItemString(dict, key);
            if (result != NULL) {
                Py_INCREF(result);
            }
            return result;
        }
        ''',
        resultspec="O",
        argspec='OsO',
        arguments=("PyObject* dict", "char* key", "PyObject* defaultValue"),
        callfunction="wrap_PyDict_GetItemString",
        cmpfunc=unhandled_error_compare
    )

    # PyDict_DelItemString
    test_PyDict_DelItemString = CPyExtFunction(
        _reference_del_item,
        lambda: (({}, "a"), ({'a': "hello"}, "a"), ({'a': "hello"}, "b")),
        resultspec="i",
        argspec='Os',
        arguments=("PyObject* dict", "char* key"),
        cmpfunc=unhandled_error_compare
    )

    # PyDict_Next
    test_PyDict_Next = CPyExtFunctionOutVars(
        _reference_next,
        #lambda: (({'a': "hello"}, 0), ({'a': "hello", 'b': 'world'}, 1), ({'a': "hello"}, 1)),
        lambda: (({'a': "hello"}, 1), ),
        code='''int wrap_PyDict_Next(PyObject* dict, Py_ssize_t* ppos, PyObject** key, PyObject** value) {
            int res = 0;
            Py_ssize_t iterations = *ppos;
            Py_ssize_t i;
            *ppos = 0;
            for(i=0; i < iterations; i++) {
                PyDict_Next(dict, ppos, key, value);
            }
            res = PyDict_Next(dict, ppos, key, value);
            if (!res) {
                // avoid problems when building the result value
                *key = dict;
                *value = dict;
                Py_INCREF(dict);
                Py_INCREF(dict);
            }
            return res;
        }
        ''',
        resultspec="iOO",
        argspec='On',
        arguments=("PyObject* dict", "Py_ssize_t ppos"),
        argumentnames=("dict, &ppos"),
        resultvars=("PyObject* key", "PyObject* value"),
        callfunction="wrap_PyDict_Next",
        cmpfunc=lambda x, y: type(x) == tuple and type(y) == tuple and len(x) == 3 and len(y) == 3 and (x[0] == 0 and y[0] == 0 or x == y) 
    )

    # PyDict_Size
    test_PyDict_Size = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (({},), ({'a': "hello"},), ({'a': "hello", 'b': "world"},)),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* dict"],
    )

    # PyDict_Copy
    test_PyDict_Copy = CPyExtFunction(
        _reference_copy,
        lambda: (
            ({},), 
            ({'a': "hello"},), 
            ({'a': "hello", 'b': "world"},),
            (tuple(),)
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* dict"],
        cmpfunc=unhandled_error_compare
    )

    # PyDict_Contains
    test_PyDict_Contains = CPyExtFunction(
        lambda args: int(args[1] in args[0]),
        lambda: (({},"a"), ({'a': "hello"},"a"), ({'a': "hello"},"b")),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* dict", "PyObject* key"],
    )
