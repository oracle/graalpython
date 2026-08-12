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

from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare


def _reference_get_item(args):
    try:
        d = args[0]
        return d.get(args[1])
    except Exception:
        return None


def _reference_keys(args):
    d = args[0]
    return list(d.keys())


def _reference_values(args):
    d = args[0]
    return list(d.values())


def _reference_items(args):
    d = args[0]
    return list(d.items())


def _reference_pop(args):
    d = args[0]
    if(len(args) == 2):
        return d.pop(args[1])
    else:
        return d.pop(args[1], args[2])


def _reference_get_item_with_error(args):
    d = args[0]
    return d.get(args[1])


def _reference_get_item_ref(args):
    d, key = args
    if not isinstance(d, dict):
        raise SystemError
    try:
        return 1, d[key]
    except KeyError:
        return 0, None


def _reference_get_item_string_ref(args):
    return _reference_get_item_ref(args)


def _reference_pydict_pop(args):
    d, key, want_result = args
    if not isinstance(d, dict):
        raise SystemError
    try:
        value = d.pop(key)
    except KeyError:
        return 0, None, d
    return 1, value if want_result else None, d


def _reference_set_item(args):
    try:
        d = args[0]
        d[args[1]] = args[2]
        return 0
    except:
        raise SystemError


def _reference_setdefault(args):
    d, key, default = args
    return d.setdefault(key, default), d


def _reference_setdefault_ref(args):
    d, key, default, want_result = args
    if not isinstance(d, dict):
        raise SystemError
    already_present = key in d
    value = d.setdefault(key, default)
    return (1 if already_present else 0), (value if want_result else None), d


def _reference_del_item(args):
    d = args[0]
    del d[args[1]]
    return 0


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
    return args[1] in args[0]


def _reference_clear(args):
    d = args[0]
    d.clear()
    return d


def _reference_merge(args):
    try:
        a, b, override = args
        if override:
            a.update(b)
        else:
            for k in b.keys():
                if not k in a:
                    a[k] = b[k]
        return 0
    except:
        raise AttributeError("'int' object has no attribute 'keys'")

class SubDict(dict):
    pass


class DictWithOverriddenLen(dict):
    def __len__(self):
        return 42


ExampleDict = {}


def fresh_dict():
    global ExampleDict
    ExampleDict = {}
    return ExampleDict


class BadEq:
    def __init__(self, s):
        self.s = s

    def __eq__(self, other):
        raise RuntimeError("boom")

    def __hash__(self):
        return hash(self.s)


class MappingObj:
    def keys(self):
        return "ab"
    def __getitem__(self, key):
        return key

class TestPyDict(CPyExtTestCase):

    # _PyDict_Pop
    test__PyDict_Pop = CPyExtFunction(
        _reference_pop,
        lambda: (({}, "a", "42"), ({'a': "hello"}, "a", "42"), ({'a': "hello"}, "b", "42"), ({BadEq('a'): "hello"}, "a", "42" )),
        resultspec="O",
        argspec='OOO',
        arguments=("PyObject* dict", "PyObject* key", "PyObject* deflt"),
        cmpfunc=unhandled_error_compare
    )

    # PyDict_Pop
    test_PyDict_Pop = CPyExtFunction(
        _reference_pydict_pop,
        lambda: (
            ({}, "missing", True),
            ({"key": "value"}, "key", True),
            ({"key": "value"}, "key", False),
            ({}, [], True),
            ([], "key", True),
            ({BadEq("key"): "value"}, "key", True),
        ),
        code='''PyObject* wrap_PyDict_Pop(PyObject* dict, PyObject* key, int want_result) {
            PyObject* result = (PyObject*)0xdeadbeef;
            int status = PyDict_Pop(dict, key, want_result ? &result : NULL);
            if (status < 0) {
                if (want_result) {
                    assert(result == NULL);
                }
                return NULL;
            }
            assert(status == 0 || status == 1);
            if (want_result) {
                assert((status == 0) == (result == NULL));
            }
            PyObject* value = want_result && result != NULL ? result : Py_NewRef(Py_None);
            return Py_BuildValue("iNN", status, value, Py_NewRef(dict));
        }''',
        resultspec="N",
        argspec='OOp',
        arguments=("PyObject* dict", "PyObject* key", "int want_result"),
        callfunction="wrap_PyDict_Pop",
        cmpfunc=unhandled_error_compare
    )

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
        lambda: (({}, "a"), ({'a': "hello"}, "a"), ({'a': "hello"}, "b"), ({BadEq('a'): "hello"}, "a")),
        code='''PyObject* wrap_PyDict_GetItem(PyObject* dict, PyObject* key) {
            PyObject* result = PyDict_GetItem(dict, key);
            if (result != NULL) {
                Py_INCREF(result);
                return result;
            }
            Py_RETURN_NONE;
        }''',
        resultspec="O",
        argspec='OO',
        arguments=("PyObject* dict", "PyObject* key"),
        callfunction="wrap_PyDict_GetItem",
        cmpfunc=unhandled_error_compare
    )

    test_PyDict_GetItem_preserves_exception = CPyExtFunction(
        lambda args: True,
        lambda: (({}, "missing"), ({"key": "value"}, "key")),
        code='''PyObject* wrap_PyDict_GetItem_preserves_exception(PyObject* dict, PyObject* key) {
            PyErr_SetString(PyExc_ValueError, "preserved");
            PyDict_GetItem(dict, key);
            int preserved = PyErr_ExceptionMatches(PyExc_ValueError);
            PyErr_Clear();
            return PyBool_FromLong(preserved);
        }''',
        resultspec="O",
        argspec='OO',
        arguments=("PyObject* dict", "PyObject* key"),
        callfunction="wrap_PyDict_GetItem_preserves_exception",
        cmpfunc=unhandled_error_compare
    )

    # PyDict_GetItemWithError
    test_PyDict_GetItemWithError = CPyExtFunction(
        _reference_get_item_with_error,
        lambda: (({}, "a"), ({'a': "hello"}, "a"), ({'a': "hello"}, "b"), ({BadEq('a'): "hello"}, "a")),
        code='''PyObject* wrap_PyDict_GetItemWithError(PyObject* dict, PyObject* key) {
            PyObject* result = PyDict_GetItemWithError(dict, key);
            if (result != NULL) {
                Py_INCREF(result);
                return result;
            }
            if (PyErr_Occurred()) {
                return NULL;
            }
            Py_RETURN_NONE;
        }''',
        resultspec="O",
        argspec='OO',
        arguments=("PyObject* dict", "PyObject* key"),
        callfunction="wrap_PyDict_GetItemWithError",
        cmpfunc=unhandled_error_compare
    )

    # PyDict_GetItemRef
    test_PyDict_GetItemRef = CPyExtFunction(
        _reference_get_item_ref,
        lambda: (
            ({}, "missing"),
            ({"key": "present"}, "key"),
            ({}, []),
            ([], "key"),
        ),
        code='''PyObject* wrap_PyDict_GetItemRef(PyObject* dict, PyObject* key) {
            PyObject* result = (PyObject*)0xdeadbeef;
            int status = PyDict_GetItemRef(dict, key, &result);
            if (status < 0) {
                assert(result == NULL);
                return NULL;
            }
            assert((status == 0) == (result == NULL));
            PyObject* value = result == NULL ? Py_NewRef(Py_None) : result;
            return Py_BuildValue("iN", status, value);
        }''',
        resultspec="N",
        argspec='OO',
        arguments=("PyObject* dict", "PyObject* key"),
        callfunction="wrap_PyDict_GetItemRef",
        cmpfunc=unhandled_error_compare
    )

    # PyDict_GetItemStringRef
    test_PyDict_GetItemStringRef = CPyExtFunction(
        _reference_get_item_string_ref,
        lambda: (
            ({}, "missing"),
            ({"key": "present"}, "key"),
            ([], "key"),
        ),
        code='''PyObject* wrap_PyDict_GetItemStringRef(PyObject* dict, const char* key) {
            PyObject* result = (PyObject*)0xdeadbeef;
            int status = PyDict_GetItemStringRef(dict, key, &result);
            if (status < 0) {
                assert(result == NULL);
                return NULL;
            }
            assert((status == 0) == (result == NULL));
            PyObject* value = result == NULL ? Py_NewRef(Py_None) : result;
            return Py_BuildValue("iN", status, value);
        }''',
        resultspec="N",
        argspec='Os',
        arguments=("PyObject* dict", "const char* key"),
        callfunction="wrap_PyDict_GetItemStringRef",
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
        lambda: (({}, "a"), ({'a': "hello"}, "a"), ({'a': "hello"}, "b"), ({BadEq('a'): "hello"}, "a")),
        code='''PyObject* wrap_PyDict_GetItemString(PyObject* dict, char* key) {
            PyObject* result = PyDict_GetItemString(dict, key);
            if (result != NULL) {
                Py_INCREF(result);
                return result;
            }
            Py_RETURN_NONE;
        }
        ''',
        resultspec="O",
        argspec='Os',
        arguments=("PyObject* dict", "char* key"),
        resulttype="PyObject*",
        callfunction="wrap_PyDict_GetItemString",
        cmpfunc=unhandled_error_compare
    )

    test_PyDict_GetItemString_preserves_exception = CPyExtFunction(
        lambda args: True,
        lambda: (({}, "missing"), ({"key": "value"}, "key")),
        code='''PyObject* wrap_PyDict_GetItemString_preserves_exception(PyObject* dict, char* key) {
            PyErr_SetString(PyExc_ValueError, "preserved");
            PyDict_GetItemString(dict, key);
            int preserved = PyErr_ExceptionMatches(PyExc_ValueError);
            PyErr_Clear();
            return PyBool_FromLong(preserved);
        }''',
        resultspec="O",
        argspec='Os',
        arguments=("PyObject* dict", "char* key"),
        callfunction="wrap_PyDict_GetItemString_preserves_exception",
        cmpfunc=unhandled_error_compare
    )

    # _PyDict_GetItemStringWithError
    test_PyDict_GetItemStringWithError = CPyExtFunctionOutVars(
        _reference_get_item_with_error,
        lambda: (({}, "a"), ({'a': "hello"}, "a"), ({'a': "hello"}, "b"), ({BadEq('a'): "hello"}, "a")),
        code='''PyObject* wrap_PyDict_GetItemStringWithError(PyObject* dict, char* key) {
            PyObject* result = _PyDict_GetItemStringWithError(dict, key);
            if (result != NULL) {
                Py_INCREF(result);
                return result;
            }
            if (PyErr_Occurred()) {
                return NULL;
            }
            Py_RETURN_NONE;
        }
        ''',
        resultspec="O",
        argspec='Os',
        arguments=("PyObject* dict", "char* key"),
        resulttype="PyObject*",
        callfunction="wrap_PyDict_GetItemStringWithError",
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
        lambda: (({'a': "hello"}, 1), ({'a': "hello"}, 0), ({'a': "hello", 'b': 'world'}, 1), ({'a': "hello"}, 1), ({True: False}, 0)),
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

    # _PyDict_SetItem_KnownHash
    test__PyDict_SetItem_KnownHash = CPyExtFunction(
        lambda args: {args[0]: args[1]},
        lambda: (('a', "hello"),),
        code='''#define Py_BUILD_CORE
        #include "internal/pycore_dict.h"
        #undef Py_BUILD_CORE

        PyObject* wrap__PyDict_SetItem_KnownHash(PyObject* key, PyObject* value) {
            PyObject* result = PyDict_New();
            if (result == NULL) {
                return NULL;
            }
            Py_hash_t phash = PyObject_Hash(key);
            if (phash == -1) {
                Py_DECREF(result);
                return NULL;
            }
            if (_PyDict_SetItem_KnownHash(result, key, value, phash) < 0) {
                Py_DECREF(result);
                return NULL;
            }
            return result;
        }
        ''',
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* key", "PyObject* value"],
        callfunction="wrap__PyDict_SetItem_KnownHash",
    )

    # PyDict_SetDefault
    test_PyDict_SetDefault = CPyExtFunction(
        _reference_setdefault,
        lambda: (
            ({}, 1, 2),
            ({1: 3}, 1, 2),
            ({}, "a", "hello"),
            ({"a": "existing"}, "a", "default"),
        ),
        code='''PyObject* wrap_PyDict_SetDefault(PyObject* dict, PyObject* key, PyObject* deflt) {
            PyObject* result = PyDict_SetDefault(dict, key, deflt);
            if (result == NULL) {
                return NULL;
            }
            Py_INCREF(result);
            return Py_BuildValue("NN", result, Py_NewRef(dict));
        }''',
        resultspec="O",
        argspec='OOO',
        arguments=("PyObject* dict", "PyObject* key", "PyObject* deflt"),
        callfunction="wrap_PyDict_SetDefault",
        cmpfunc=unhandled_error_compare
    )

    # PyDict_SetDefaultRef
    test_PyDict_SetDefaultRef = CPyExtFunction(
        _reference_setdefault_ref,
        lambda: (
            ({}, "key", "inserted", True),
            ({"key": "present"}, "key", "default", True),
            ({}, "key", "inserted", False),
            ({"key": "present"}, "key", "default", False),
            ({}, [], "default", True),
            ([], "key", "default", True),
        ),
        code='''PyObject* wrap_PyDict_SetDefaultRef(PyObject* dict, PyObject* key, PyObject* deflt, int want_result) {
            PyObject* result = (PyObject*)0xdeadbeef;
            int status = PyDict_SetDefaultRef(dict, key, deflt, want_result ? &result : NULL);
            if (status < 0) {
                if (want_result) {
                    assert(result == NULL);
                }
                return NULL;
            }
            assert(status == 0 || status == 1);
            PyObject* value = want_result ? result : Py_NewRef(Py_None);
            return Py_BuildValue("iNN", status, value, Py_NewRef(dict));
        }''',
        resultspec="N",
        argspec='OOOp',
        arguments=("PyObject* dict", "PyObject* key", "PyObject* deflt", "int want_result"),
        callfunction="wrap_PyDict_SetDefaultRef",
        cmpfunc=unhandled_error_compare
    )

    # PyDict_Size
    test_PyDict_Size = CPyExtFunction(
        lambda args: len(args[0]),
        lambda: (({},), ({'a': "hello"},), ({'a': "hello", 'b': "world"},)),
        resultspec="n",
        argspec='O',
        arguments=["PyObject* dict"],
    )

    test_PyDict_GET_SIZE = CPyExtFunction(
        lambda args: dict.__len__(args[0]),
        lambda: (
            ({},),
            ({'a': "hello"},),
            ({'a': "hello", 'b': "world"},),
            (SubDict({'a': "hello"}),),
            (DictWithOverriddenLen({'a': "hello", 'b': "world"}),),
        ),
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

    test_PyDictView_Type = CPyExtFunction(
        lambda args: True,
        lambda: (
            ({}.keys(), 0),
            ({}.items(), 1),
            ({}.values(), 2),
        ),
        code='''int wrap_PyDictView_Type(PyObject* view, int kind) {
            PyTypeObject* expected_types[] = {
                &PyDictKeys_Type,
                &PyDictItems_Type,
                &PyDictValues_Type,
            };
            return Py_IS_TYPE(view, expected_types[kind]);
        }''',
        resultspec="i",
        argspec="Oi",
        arguments=["PyObject* view", "int kind"],
        callfunction="wrap_PyDictView_Type",
    )

    test_PyDictIter_Type = CPyExtFunction(
        lambda args: True,
        lambda: (
            (iter({}), 0),
            (iter({}.values()), 1),
            (iter({}.items()), 2),
        ),
        code='''int wrap_PyDictIter_Type(PyObject* iterator, int kind) {
            PyTypeObject* expected_types[] = {
                &PyDictIterKey_Type,
                &PyDictIterValue_Type,
                &PyDictIterItem_Type,
            };
            return Py_IS_TYPE(iterator, expected_types[kind]);
        }''',
        resultspec="i",
        argspec="Oi",
        arguments=["PyObject* iterator", "int kind"],
        callfunction="wrap_PyDictIter_Type",
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
            (dict({"a": 1}), MappingObj(), 0),
            (dict({"a": 1}), MappingObj(), 1),
            (dict({"a": 1}), 1, 1),
            (dict({"a": 1}), 1, 1),
        ),
        resultspec="i",
        argspec="OOi",
        arguments=["PyObject* a", "PyObject* b", "int override"],
        cmpfunc=unhandled_error_compare
    )

    # PyDict_Keys
    test_PyDict_Keys = CPyExtFunction(
        _reference_keys,
        lambda: (({},), ({'a': "hello"},)),
        resultspec="O",
        argspec="O",
        cmpfunc=unhandled_error_compare
    )

    # PyDict_Values
    test_PyDict_Values = CPyExtFunction(
        _reference_values,
        lambda: (({},), ({'a': "hello"},)),
        resultspec="O",
        argspec="O",
        cmpfunc=unhandled_error_compare
    )

    test_PyDict_Items = CPyExtFunction(
        _reference_items,
        lambda: (({},), ({'a': "hello"},)),
        resultspec="O",
        argspec="O",
        cmpfunc=unhandled_error_compare
    )
