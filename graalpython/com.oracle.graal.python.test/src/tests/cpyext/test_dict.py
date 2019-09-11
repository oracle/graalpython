# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return -1


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


def _reference_contains(args):
    try:
        return args[1] in args[0]
    except:
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            return -1


def _reference_clear(args):
    try:
        d = args[0]
        d.clear()
        return d
    except:
        raise SystemError


def _reference_merge(args):
    a, b, override = args
    if override:
        a.update(b)
    else:
        for k in b:
            if not k in a:
                a[k] = b[k]
    return 0

class SubDict(dict):
    pass


ExampleDict = {}


def fresh_dict():
    global ExampleDict
    ExampleDict = {}
    return ExampleDict


class TestPyDict(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyDict, self).compile_module(name)

    # PyDict_SetItem
    test_PyDict_SetItem = CPyExtFunction(
        _reference_set_item,
        lambda: (
            ({}, "a", "hello")
            , ({'a': "hello"}, "b", "world")
            # mappingproxy
            , (type(type.__dict__)({'a': "hello"}), "b", "world")
            ),
        resultspec="i",
        argspec='OOO',
        arguments=("PyObject* dict", "PyObject* key", "PyObject* val"),
        cmpfunc=unhandled_error_compare
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
        resulttype="PyObject*",
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
        # lambda: (({'a': "hello"}, 0), ({'a': "hello", 'b': 'world'}, 1), ({'a': "hello"}, 1)),
        lambda: (({'a': "hello"}, 1),),
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
        resulttype="int",
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
        _reference_contains,
        lambda: (
            ({}, "a"),
            ({'a': "hello"}, "a"),
            ({'a': "hello"}, "b"),
            ({'a': "hello"}, ("a", "b")),
            ({'a': "hello"}, {"a", "b"}),
            ({'a': "hello"}, ["a", "b"]),
        ),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* dict", "PyObject* key"],
        cmpfunc=unhandled_error_compare
    )

    test_PyDict_Check = CPyExtFunction(
        lambda args: isinstance(args[0], dict),
        lambda: (
            ({},),
            ({'a': "hello"},),
            (dict(),),
            ("not a dict",),
            (3,),
            (tuple(),),
            ([],),
            (SubDict(),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
    )

    test_PyDict_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is dict,
        lambda: (
            ({},),
            ({'a': "hello"},),
            (dict(),),
            ("not a dict",),
            (3,),
            (tuple(),),
            ([],),
            (SubDict(),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
    )

    test_PyDict_Update = CPyExtFunction(
        lambda args: 1 if args[0].update(args[1]) else 0,
        lambda: (
            (fresh_dict(), {"a": 1}),
        ),
        resultspec="O",
        argspec="OO",
        arguments=["PyObject* self", "PyObject* other"],
        code='''PyObject* wrap_PyDict_Update(PyObject* self, PyObject* other) {
            int result = PyDict_Update(self, other);
            if (result == -1) {
                return NULL;
            } else {
                return PyLong_FromLong(result);
            }
        }''',
        callfunction="wrap_PyDict_Update",
        cmpfunc=lambda cr, pr: (cr == pr or (isinstance(cr, BaseException) and type(cr) == type(pr))) and (ExampleDict.get("a") == 1 or len(ExampleDict) == 0)
    )

    test_PyDict_Clear = CPyExtFunction(
        _reference_clear,
        lambda: (
            (dict({"a": 1}), ),
            (dict(), ),
        ),
        resultspec="O",
        argspec="O",
        arguments=["PyObject* self"],
        code='''PyObject* wrap_PyDict_Clear(PyObject* self) {
            PyDict_Clear(self);
            return self;
        }''',
        callfunction="wrap_PyDict_Clear",
        cmpfunc=unhandled_error_compare
    )

    test_PyDict_Merge = CPyExtFunction(
        _reference_merge,
        lambda: (
            (dict({"a": 1}), {"b": 2}, 0),
            (dict(), {"b": 2}, 0),
            (dict({"a": 1}), {"a": 2}, 0),
            (dict({"a": 1}), {"a": 2}, 1),
        ),
        resultspec="i",
        argspec="OOi",
        arguments=["PyObject* a", "PyObject* b", "int override"],
        cmpfunc=unhandled_error_compare
    )
