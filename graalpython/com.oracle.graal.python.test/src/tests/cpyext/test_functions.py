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

import os
import sys
import unittest

from . import CPyExtType, CPyExtTestCase, CPyExtFunction, unhandled_error_compare, CPyExtHeapType, RUNS_ON_LLVM

DIR = os.path.dirname(__file__)


class CallableIter:
    def __init__(self, start):
        self.idx = start
    
    def __call__(self, *args):
        cur = self.idx
        self.idx += 1
        return cur


def kw_fun(a, b=0, c=0):
    return {"a": a, "b": b, "c": c}


def kwonly_fun(**kwargs):
    return kwargs


def ident(arg, *others):
    return (len(others), arg, others)


def identCallResult(arg):
    if type(arg) is tuple:
        return ident(*arg)
    else:
        return ident(arg)


class TypeWithAttr:
    attr = "str"


class SubtypeWithAttr(TypeWithAttr):
    pass


class TypeWithAttrInDict:
    attr = "str"


TypeWithAttrInDict.__dict__

NativeTypeWithAttr = CPyExtHeapType("NativeTypeWithAttr")
NativeTypeWithAttr.attr = "str"


class TestPyObject(CPyExtTestCase):

    test_Py_TYPE = CPyExtFunction(
        type,
        lambda: ([], 12, sys.modules),
        resulttype = "PyTypeObject*"
    )

    # Below are the PyObject_* identifiers that we know are used in numpy

    # test_PyObject_CheckBuffer = CPyExtFunction(
    #     is_buffer,
    #     (b"abc", bytearray(b"abc")),
    #     resultspec="i",
    # )
    # PyObject_Del
    # PyObject_FREE
    # PyObject_Free

    # PyObject_MALLOC
    # PyObject_Malloc
    # PyObject_New
    # PyObject_REALLOC
    # PyObject_Realloc
    test_PyObject_TypeCheck = CPyExtFunction(
        lambda args: 1 if isinstance(*args) else 0,
        lambda: (
            (1, int),
            ("hello", str),
            (True, bool),
            (True, int),
        ),
        argspec="OO",
        arguments=["PyObject* op", "PyTypeObject* type"],
        resultspec="i",
    )

    __PyObject_Call_ARGS = (
        (len, ((1, 2, 3),), {}),
        (sum, ((0, 1, 2),), {}),
        (format, (object(),), {"format_spec": ""}),
        (sum, ("hello, world",), {}),
        (kw_fun, (123,), {"c": 456, "b": 789}),
        (kwonly_fun, tuple(), {"x": 456, "y": 789}),
        (sum, ("hello, world",), None),
        (kwonly_fun, tuple(), None),
    )

    test_PyObject_Call = CPyExtFunction(
        lambda args: args[0](*args[1], **args[2]) if args[2] else args[0](*args[1], **dict()),
        lambda: TestPyObject.__PyObject_Call_ARGS,
        code='''#include <stdio.h>
        PyObject * wrap_PyObject_Call(PyObject *callable, PyObject *args, PyObject *kwargs) {
            if(kwargs == Py_None) {
                return PyObject_Call(callable, args, NULL);
            }
            return PyObject_Call(callable, args, kwargs);
        }
        ''',
        arguments=["PyObject* callable", "PyObject* callargs", "PyObject* kwargs"],
        argspec="OOO",
        callfunction="wrap_PyObject_Call",
    )
    test_PyObject_CallNoArgs = CPyExtFunction(
        lambda args: args[0](),
        lambda: (
            (dict, ),
            (list, ),
        ),
        arguments=["PyObject* callable"],
        argspec="O",
    )
    test_PyObject_CallObject = CPyExtFunction(
        lambda args: args[0](*args[1]),
        lambda: (
            (len, ((1, 2, 3),)),
            (sum, ((0, 1, 2),)),
        ),
        arguments=["PyObject* callable", "PyObject* callargs"],
        argspec="OO",
    )
    test_PyObject_CallFunction = CPyExtFunction(
        lambda args: args[0](args[2], args[3]),
        lambda: (
            (sum, "Oi", [], 10),
            (sum, "Oi", [], 10),
        ),
        arguments=["PyObject* callable", "const char* fmt", "PyObject* list", "int initial"],
        argspec="OsOi",
    )
    test_PyObject_CallFunction0 = CPyExtFunction(
        lambda args: args[0](),
        lambda: (
            (list, ""),
            (bool, ""),
        ),
        arguments=["PyObject* callable", "const char* fmt"],
        argspec="Os",
        callfunction="PyObject_CallFunction",
    )
    test_PyObject_CallFunction1 = CPyExtFunction(
        lambda args: identCallResult(args[2]),
        lambda: (
            (ident, "O", "123"),
            (ident, "O", "12"),
            (ident, "O", tuple()),
            (ident, "O", ("12",)),
            (ident, "O", ("12", "13")),
            (ident, "O", ("12", "13", "asdf")),
        ),
        arguments=["PyObject* callable", "const char* fmt", "PyObject* arg"],
        argspec="OsO",
        callfunction="PyObject_CallFunction",
    )

    class MyObject():

        def foo(self, *args, **kwargs):
            return sum(*args, **kwargs)

        def __hash__(self):
            return 42

    test_PyObject_CallMethod = CPyExtFunction(
        lambda args: getattr(args[0], args[1])(args[3], args[4]),
        lambda: (
            (TestPyObject.MyObject(), "foo", "Oi", [], 10),
        ),
        arguments=["PyObject* rcvr", "const char* method", "const char* fmt", "PyObject* list", "int initial"],
        argspec="OssOi",
    )
    test_PyObject_CallMethod0 = CPyExtFunction(
        lambda args: getattr(args[0], args[1])(),
        lambda: (
            ([3,4,5],"__inexisting_method__", ""),
            ([1,2,3,4],"__len__", ""),
        ),
        arguments=["PyObject* callable", "char* method_name", "char* fmt"],
        resultspec="O",
        argspec="Oss",
        callfunction="PyObject_CallMethod"
    )
    test_PyObject_Type = CPyExtFunction(
        type,
        lambda: (
            1, 0, [], {}, CPyExtFunction,
        )
    )
    test_PyObject_GetItem = CPyExtFunction(
        lambda args: args[0][args[1]],
        lambda: (
            ([1, 2, 3], 1),
            # ( {"a": 42}, "b" ),
        ),
        arguments=["PyObject* primary", "PyObject* item"],
        argspec="OO",
    )

    def forgiving_set_item(args):
        args[0][args[1]] = args[2]
        return 0

    test_PyObject_SetItem = CPyExtFunction(
        forgiving_set_item,
        lambda: (
            ([1, 2, 3], 1, 12),
            ({"a": 32}, "b", 42),
            ((1, 2), 0, 1),
            ([], 0, 1),
            ([], "a", 1),
            ({}, CPyExtFunction(1, 2), "hello"),
        ),
        arguments=["PyObject* primary", "PyObject* key", "PyObject* value"],
        argspec="OOO",
        resultspec="i",
        cmpfunc=unhandled_error_compare
    )
    # PyObject_AsReadBuffer
    # PyObject_AsWriteBuffer
    # PyObject_GetBuffer
    test_PyObject_Format = CPyExtFunction(
        lambda args: args[0].__format__(args[1]),
        lambda: (
            ([], ""),
            ({}, ""),
            (1, ""),
        ),
        arguments=["PyObject* object", "PyObject* format_spec"],
        argspec="OO",
    )

    test_PyObject_GetIter = CPyExtFunction(
        iter,
        lambda: ([], {}, (0,)),
        cmpfunc=(lambda x, y: type(x) == type(y))
    )

    test_PyCallIter_New = CPyExtFunction(
        lambda args: iter(args[0], args[1]),
        lambda: (
            (lambda: 1, 1),
            (CallableIter(0), 10),
            (CallableIter(5), 7),
            (CallableIter(5), 5),
        ),
        arguments=["PyObject* callable", "PyObject* sentinel"],
        argspec="OO",
        resultspec="O",
        cmpfunc=(lambda x, y: list(x) == list(y))
    )

    test_PyObject_IsInstance = CPyExtFunction(
        lambda args: 1 if isinstance(*args) else 0,
        lambda: (
            ([1, 2], list),
            (1, int),
            (1, list),
        ),
        arguments=["PyObject* object", "PyObject* type"],
        argspec="OO",
        resultspec="i",
    )

    if not RUNS_ON_LLVM:
        __PyObject_AsFileDescriptor_FD0 = open(1, buffering=0, mode="wb")
        __PyObject_AsFileDescriptor_FD1 = open("%s/As_FileDescriptor_Testfile" % DIR, buffering=0, mode="wb")
        test_PyObject_AsFileDescriptor = CPyExtFunction(
            lambda arg: arg if isinstance(arg, int) else arg.fileno(),
            lambda: (
                1,
                TestPyObject.__PyObject_AsFileDescriptor_FD0,
                TestPyObject.__PyObject_AsFileDescriptor_FD1,
            ),
            resultspec="i",
        )

    test_PyObject_Print = CPyExtFunction(
        lambda args: 0,
        lambda: ([], 1, "a"),
        arguments=["PyObject* object", "FILE* fp=fdopen(1,\"w\")", "int flags=0"],
        resultspec="i",
    )
    test_PyObject_Repr = CPyExtFunction(
        repr,
        lambda: ([], {}, 0, 123, b"ello")
    )
    test_PyObject_Str = CPyExtFunction(
        repr,
        lambda: ([], {}, 0, 123, b"ello")
    )

    richcompare_args = lambda: (([], [], 0),
                                ([], [], 1),
                                ([], [], 2),
                                ([], [], 3),
                                ([], [], 4),
                                ([], [], 5),
                                (12, 24, 0),
                                (12, 24, 1),
                                (12, 24, 2),
                                (12, 24, 3),
                                (12, 24, 4),
                                (12, 24, 5),
                                ("aa", "ba", 0),
                                ("aa", "ba", 1),
                                ("aa", "ba", 2),
                                ("aa", "ba", 3),
                                ("aa", "ba", 4),
                                ("aa", "ba", 5))

    def richcompare(args):
        return eval("%r %s %r" % (args[0], ["<", "<=", "==", "!=", ">", ">="][args[2]], args[1]))

    test_PyObject_RichCompare = CPyExtFunction(
        richcompare,
        richcompare_args,
        arguments=["PyObject* left", "PyObject* right", "int op"],
        argspec="OOi",
        resultspec="O",
    )

    def richcompare_bool(args):
        try:
            if eval("%r %s %r" % (args[0], ["<", "<=", "==", "!=", ">", ">="][args[2]], args[1])):
                return 1
            else:
                return 0
        except:
            return -1

    test_PyObject_RichCompareBool = CPyExtFunction(
        richcompare_bool,
        richcompare_args,
        arguments=["PyObject* left", "PyObject* right", "int op"],
        argspec="OOi",
        resultspec="i",
    )

    class TypeWithGetattr:
        def __getattr__(self, item):
            return item

    __PyObject_GetAttrString_ARGS = (
        (MyObject(), "foo"),
        ([], "__len__"),
        (TypeWithGetattr(), "foo"),
    )
    test_PyObject_GetAttrString = CPyExtFunction(
        lambda args: getattr(*args),
        lambda: TestPyObject.__PyObject_GetAttrString_ARGS,
        arguments=["PyObject* object", "const char* attr"],
        argspec="Os",
    )

    test__PyType_Lookup = CPyExtFunction(
        lambda args: "str",
        lambda: (
            (TypeWithAttr, "attr"),
            (TypeWithAttr, "attr"),
            (SubtypeWithAttr, "attr"),
            (SubtypeWithAttr, "attr"),
            (TypeWithAttrInDict, "attr"),
            (TypeWithAttrInDict, "attr"),
            (NativeTypeWithAttr, "attr"),
            (NativeTypeWithAttr, "attr"),
        ),
        arguments=["PyTypeObject* type", "PyObject* name"],
        argspec="OO",
    )

    def setattrstring(args):
        setattr(*args)
        return 0

    test_PyObject_SetAttrString = CPyExtFunction(
        setattrstring,
        lambda: (
            (TestPyObject.MyObject, "x", 42),
            ([], "x", 42),
        ),
        arguments=["PyObject* object", "const char* attr", "PyObject* value"],
        argspec="OsO",
        resultspec="i",
        cmpfunc=unhandled_error_compare
    )
    test_PyObject_HasAttr = CPyExtFunction(
        lambda args: 1 if hasattr(*args) else 0,
        lambda: (
            (TestPyObject.MyObject, "foo"),
            ([], "__len__"),
            ([], "foobar"),
        ),
        arguments=["PyObject* object", "PyObject* attr"],
        argspec="OO",
        resultspec="i",
    )

    test_PyObject_HasAttrString = CPyExtFunction(
        lambda args: 1 if hasattr(*args) else 0,
        lambda: (
            (TestPyObject.MyObject, "foo"),
            ([], "__len__"),
            ([], "foobar"),
        ),
        arguments=["PyObject* object", "const char* attr"],
        argspec="Os",
        resultspec="i",
    )

    def _ref_hash_not_implemented(args):
        raise TypeError

    test_PyObject_HashNotImplemented = CPyExtFunction(
        _ref_hash_not_implemented,
        lambda: (
            ("foo",),
        ),
        arguments=["PyObject* object"],
        argspec="O",
        resultspec="n",
        cmpfunc=unhandled_error_compare
    )
    __PyObject_GetAttr_ARGS = (
        (MyObject(), "foo"),
        ([], "__len__"),
        (TypeWithGetattr(), "foo"),
    )
    test_PyObject_GetAttr = CPyExtFunction(
        lambda args: getattr(*args),
        lambda: TestPyObject.__PyObject_GetAttr_ARGS,
        arguments=["PyObject* object", "PyObject* attr"],
        argspec="OO",
    )
    test_PyObject_GenericGetAttr = CPyExtFunction(
        lambda args: object.__getattribute__(*args),
        lambda: TestPyObject.__PyObject_GetAttr_ARGS,
        arguments=["PyObject* object", "PyObject* attr"],
        argspec="OO",
    )

    test_PyObject_SelfIter = CPyExtFunction(
        lambda x: x,
        lambda: ([], 1, 42, "abc", {}, type),
    )

    test_PyObject_IsTrue = CPyExtFunction(
        lambda arg: 1 if bool(arg) else 0,
        lambda: (1, 0, -1, {}, [], [1]),
        resultspec="i",
    )
    test_PyObject_Not = CPyExtFunction(
        lambda arg: 1 if not(bool(arg)) else 0,
        lambda: (1, 0, -1, {}, [], [1], (1,)),
        resultspec="i",
    )
    # PyObject_ClearWeakRefs
    # test_PyObject_New = CPyExtFunction(
    # )
    # test_PyObject_Init = CPyExtFunction(
    # )

    test_PyStaticMethod_New = CPyExtFunction(
        lambda args: None,
        lambda: (
            (kw_fun,),
        ),
        resultspec="O",
        arguments=["PyObject* func"],
        argspec="O",
        cmpfunc=lambda x, y: type(x) == staticmethod
    )

    test_method_def_memcpy = CPyExtFunction(
        lambda args: args[0].__name__,
        lambda: (
            (bin,),
        ),
        code="""
            #include <string.h>
    
            // CPython and other don't have this function; so define it
            #ifndef GRAALVM_PYTHON
            #define _PyCFunction_GetMethodDef(OBJ) (((PyCFunctionObject*) (OBJ))->m_ml)
            #endif
    
            static PyObject* wrap_PyCFunction_GetMethodDef(PyObject* func) {
                PyMethodDef *src;
                PyMethodDef dst;
                if (PyCFunction_Check(func)) {
                    src = _PyCFunction_GetMethodDef(func);
                    dst = *src;
                    return PyUnicode_FromString(dst.ml_name);
                }
                PyErr_Format(PyExc_TypeError, "Unexpected test arg. Expected %s but got %s",
                        PyCFunction_Type.tp_name,
                        Py_TYPE(func)->tp_name);
                return NULL;
            }
            """,
        resultspec="O",
        argspec="O",
        arguments=["PyObject* func"],
        callfunction="wrap_PyCFunction_GetMethodDef",
        cmpfunc=unhandled_error_compare
    )
    # create function from PyMethodDef
    # test PyMethodDef is same
    # test calling m_meth

class TestPyCFunction(unittest.TestCase):
    def test_PyMethodDef(self):
        TestPyMethodDef = CPyExtType(
            "TestPyMethodDef",
            """
            #include <assert.h>

            // CPython and other don't have this function; so define it
            #ifndef GRAALVM_PYTHON
            #define _PyCFunction_GetMethodDef(OBJ) (((PyCFunctionObject*) (OBJ))->m_ml)
            #define _PyCFunction_SetMethodDef(OBJ, VAL) (((PyCFunctionObject*) (OBJ))->m_ml = (VAL))
            #define _PyCFunction_GetModule(OBJ) (((PyCFunctionObject*) (OBJ))->m_module)
            #define _PyCFunction_SetModule(OBJ, VAL) (((PyCFunctionObject*) (OBJ))->m_module = (VAL))
            #define PyMethodDescrObject_GetMethod(OBJ) (((PyMethodDescrObject *) (OBJ))->d_method)
            #endif

            static PyObject *native_meth_noargs(PyObject *self, PyObject *dummy) {
                return PyLong_FromLong(789);
            }

            static PyObject *native_meth_o(PyObject *self, PyObject *arg) {
                PyObject *one = PyLong_FromLong(1);
                PyObject *result = PyNumber_Add(arg, one);
                Py_DECREF(one);
                return result;
            }

            static PyObject *native_meth_varargs(PyObject *self, PyObject *args) {
                Py_INCREF(args);
                return args;
            }

            static PyObject *native_meth_keywords(PyObject *self, PyObject *args, PyObject *kwargs) {
                if (kwargs) {
                    return PyTuple_Pack(2, args, kwargs);
                }
                return PyTuple_Pack(2, args, Py_None);
            }

            static PyObject *get_name(PyObject *self, PyObject *arg) {
                if (!PyCFunction_Check(arg)) {
                    PyErr_SetString(PyExc_TypeError, "<callable> is not a PyCFunction (i.e. builtin_method_or_function)");
                    return NULL;
                }
                PyMethodDef *def = _PyCFunction_GetMethodDef(arg);
                return PyUnicode_FromString(def->ml_name);
            }

            static PyObject *get_flags(PyObject *self, PyObject *arg) {
                if (!PyCFunction_Check(arg)) {
                    PyErr_SetString(PyExc_TypeError, "<callable> is not a PyCFunction (i.e. builtin_method_or_function)");
                    return NULL;
                }
                PyMethodDef *def = _PyCFunction_GetMethodDef(arg);
                return PyLong_FromLong(def->ml_flags);
            }

            static PyObject *_call_PyCFunction(PyMethodDef *def, PyObject *callable_self, PyObject *callable_args, PyObject *callable_kwargs) {
                if (def->ml_flags == METH_NOARGS) {
                    return def->ml_meth(callable_self, NULL);
                } else if (def->ml_flags == METH_O) {
                    return def->ml_meth(callable_self, PyTuple_GetItem(callable_args, 0));;
                } else if (def->ml_flags == METH_VARARGS) {
                    return def->ml_meth(callable_self, callable_args);
                } else if (def->ml_flags == METH_FASTCALL) {
                    return ((_PyCFunctionFast) def->ml_meth)(callable_self, PySequence_Fast_ITEMS(callable_args), PyTuple_Size(callable_args));
                } else if (def->ml_flags == (METH_VARARGS | METH_KEYWORDS)) {
                    return ((PyCFunctionWithKeywords) def->ml_meth)(callable_self, callable_args, callable_kwargs);
                } else if (def->ml_flags == (METH_FASTCALL | METH_KEYWORDS)) {
                    Py_ssize_t nargs =  PyTuple_Size(callable_args);
                    if (nargs < 0) {
                        return NULL;
                    }
                    Py_ssize_t nkw =  callable_kwargs != NULL ? PyDict_Size(callable_kwargs) : 0;
                    if (nkw < 0) {
                        return NULL;
                    }
                    PyObject **args_with_kw = (PyObject **) PyMem_Calloc(nargs + nkw, sizeof(PyObject *));
                    if (args_with_kw == NULL) {
                        return NULL;
                    }
                    for (Py_ssize_t i = 0; i < nargs; i++) {
                        // borrowed is fine in this case
                        args_with_kw[i] = PyTuple_GET_ITEM(callable_args, i);
                    }

                    PyObject *kwnames = NULL;
                    if (nkw) {
                        PyObject *keys = PyDict_Keys(callable_kwargs);
                        if (keys == NULL) {
                            PyMem_Free(args_with_kw);
                            return NULL;
                        }
                        kwnames = PySequence_Tuple(keys);
                        if (kwnames == NULL) {
                            Py_DECREF(keys);
                            PyMem_Free(args_with_kw);
                            return NULL;
                        }
                        assert(nkw == PyTuple_Size(kwnames));
                        for (Py_ssize_t i = 0; i < nkw; i++) {
                            args_with_kw[nargs + i] = PyDict_GetItemWithError(callable_kwargs, PyTuple_GET_ITEM(kwnames, i));
                        }
                    }
                    PyObject *result = ((_PyCFunctionFastWithKeywords) def->ml_meth)(callable_self, args_with_kw, nargs, kwnames);
                    PyMem_Free(args_with_kw);
                    Py_XDECREF(kwnames);
                    return result;
                } else {
                    PyErr_SetString(PyExc_ValueError, "<callable> has unsupported signature");
                    return NULL;
                }
            }
            
            static PyObject *call_meth(PyObject *self, PyObject *args) {
                PyObject *callable, *callable_args, *callable_kwargs = NULL;
                if (!PyArg_ParseTuple(args, "OO|O:call_meth",
                                         &callable, &callable_args, &callable_kwargs)) {
                    PyErr_SetString(PyExc_ValueError, "required args: <callable>, <args_tuple> [, <kwargs>]");
                    return NULL;
                }
                if (!PyCFunction_Check(callable)) {
                    PyErr_SetString(PyExc_TypeError, "<callable> is not a PyCFunction (i.e. builtin_method_or_function) ");
                    return NULL;
                }
                PyMethodDef *def = _PyCFunction_GetMethodDef(callable);
                // returns a borrowed ref
                PyObject *callable_self = PyCFunction_GetSelf(callable);
                return _call_PyCFunction(def, callable_self, callable_args, callable_kwargs);
            }

            static PyObject *call_meth_descr(PyObject *self, PyObject *args) {
                PyObject *callable, *callable_self, *callable_args, *callable_kwargs = NULL;
                if (!PyArg_ParseTuple(args, "OOO|O:call_meth",
                                         &callable, &callable_self, &callable_args, &callable_kwargs)) {
                    PyErr_SetString(PyExc_ValueError, "required args: <callable>, <self>, <args_tuple> [, <kwargs>]");
                    return NULL;
                }
                if (!PyObject_TypeCheck(callable, &PyMethodDescr_Type)) {
                    PyErr_SetString(PyExc_TypeError, "<callable> is not a PyMethodDescrObject (i.e. method_descriptor)");
                    return NULL;
                }
                PyMethodDef *def = PyMethodDescrObject_GetMethod(callable);
                // returns a borrowed ref
                return _call_PyCFunction(def, callable_self, callable_args, callable_kwargs);
            }

            static PyObject *get_doc(PyObject *self, PyObject *arg) {
                if (!PyCFunction_Check(arg)) {
                    PyErr_SetString(PyExc_TypeError, "<callable> is not a PyCFunction (i.e. builtin_method_or_function)");
                    return NULL;
                }
                PyMethodDef *def = _PyCFunction_GetMethodDef(arg);
                return PyUnicode_FromString(def->ml_doc);
            }

            static PyObject *set_def(PyObject *self, PyObject *arg) {
                if (!PyCFunction_Check(arg)) {
                    PyErr_SetString(PyExc_TypeError, "<callable> is not a PyCFunction (i.e. builtin_method_or_function)");
                    return NULL;
                }
                PyMethodDef *def = _PyCFunction_GetMethodDef(arg);
                _PyCFunction_SetMethodDef(arg, def);
                return PyUnicode_FromString(_PyCFunction_GetMethodDef(arg)->ml_doc);
            }

            static PyObject *get_set_module(PyObject *self, PyObject *arg) {
                if (!PyCFunction_Check(arg)) {
                    PyErr_SetString(PyExc_TypeError, "<callable> is not a PyCFunction (i.e. builtin_method_or_function)");
                    return NULL;
                }
                PyObject *module = _PyCFunction_GetModule(arg);
                Py_XINCREF(self);
                _PyCFunction_SetModule(arg, self);
                if (_PyCFunction_GetModule(arg) != self) {
                    PyErr_SetString(PyExc_TypeError, "module of function is not self");
                    return NULL;
                }
                Py_XINCREF(self);
                return self;
            }

            static PyObject *new_meth(PyObject *self, PyObject *args) {
                PyObject *callable_type = NULL, *callable_self, *callable;
                if (!PyArg_ParseTuple(args, "OO:new_meth",
                                         &callable_self, &callable)) {
                                         // &PyType_Type, &callable_type, &callable_self, &callable)) {
                    PyErr_SetString(PyExc_ValueError, "required args: <type>, <self>, <callable>");
                    return NULL;
                }
                PyMethodDef *def;
                if (PyCFunction_Check(callable)) {
                    def = _PyCFunction_GetMethodDef(callable);
                } else if (PyObject_TypeCheck(callable, &PyMethodDescr_Type)) {
                    def = PyMethodDescrObject_GetMethod(callable);
                } else {
                    PyErr_SetString(PyExc_TypeError, "<callable> is not a PyCFunction (i.e. builtin_method_or_function) "
                                                     "nor a PyMethodDescrObject (i.e. method_descriptor)");
                    return NULL;
                }
                return PyCMethod_New(def, callable_self, NULL, (PyTypeObject *)callable_type);
            }
            """,
            tp_methods='''
            {"native_meth_noargs", (PyCFunction)native_meth_noargs, METH_NOARGS, "doc noargs"},
            {"native_meth_o", (PyCFunction)native_meth_o, METH_O, "doc o"},
            {"native_meth_varargs", (PyCFunction)native_meth_varargs, METH_VARARGS, "doc varargs"},
            {"native_meth_keywords", (PyCFunction)native_meth_keywords, METH_VARARGS | METH_KEYWORDS, "doc keywords"},
            {"get_name", (PyCFunction)get_name, METH_O, ""},
            {"get_flags", (PyCFunction)get_flags, METH_O, ""},
            {"call_meth", (PyCFunction)call_meth, METH_VARARGS, ""},
            {"call_meth_descr", (PyCFunction)call_meth_descr, METH_VARARGS, ""},
            {"get_doc", (PyCFunction)get_doc, METH_O, ""},
            {"new_meth", (PyCFunction)new_meth, METH_VARARGS, ""},
            {"set_def", (PyCFunction)set_def, METH_O, ""},
            {"get_set_module", (PyCFunction)get_set_module, METH_O, ""}
            ''',
            post_ready_code='''
            PyModule_AddIntMacro(m, METH_NOARGS);
            PyModule_AddIntMacro(m, METH_O);
            PyModule_AddIntMacro(m, METH_VARARGS);
            PyModule_AddIntMacro(m, METH_KEYWORDS);
            '''
        )
        m = __import__(TestPyMethodDef.__module__)
        tester = TestPyMethodDef()
        assert tester.get_name(tester.native_meth_noargs) == "native_meth_noargs"
        assert tester.get_flags(tester.native_meth_noargs) == m.METH_NOARGS
        assert tester.get_doc(tester.native_meth_noargs) == "doc noargs"
        assert tester.call_meth(tester.native_meth_noargs, tuple()) == 789
        assert tester.get_name(tester.native_meth_o) == "native_meth_o"
        assert tester.get_flags(tester.native_meth_o) == m.METH_O
        assert tester.get_doc(tester.native_meth_o) == "doc o"
        assert tester.call_meth(tester.native_meth_o, (123,)) == 124
        assert tester.get_name(tester.native_meth_varargs) == "native_meth_varargs"
        assert tester.get_flags(tester.native_meth_varargs) == m.METH_VARARGS
        assert tester.get_doc(tester.native_meth_varargs) == "doc varargs"
        assert tester.call_meth(tester.native_meth_varargs, (1, 2, "hello", "world")) == (1, 2, "hello", "world")
        assert tester.get_name(tester.native_meth_keywords) == "native_meth_keywords"
        assert tester.get_flags(tester.native_meth_keywords) == m.METH_VARARGS | m.METH_KEYWORDS
        assert tester.get_doc(tester.native_meth_keywords) == "doc keywords"
        assert tester.call_meth(tester.native_meth_keywords, (1, 2), {"hello": "world"}) == ((1, 2), {"hello": "world"})
        assert tester.set_def(tester.native_meth_keywords) == "doc keywords"
        assert tester.get_set_module(tester.get_set_module) == tester

        # built-in functions

        # METH_NOARGS
        assert tester.call_meth(globals, None) is not None
        # METH_O
        assert tester.get_name(bin) == "bin"
        assert tester.get_flags(bin) == m.METH_O
        assert tester.call_meth(bin, (123, )) == "0b1111011"
        # METH_VARARGS or METH_FASTCALL
        assert tester.call_meth(divmod, (123, 5)) == (24, 3)
        # METH_KEYWORDS
        # TODO

        # built-in methods
        assert tester.call_meth_descr(str.center, "abc", (10, )) == "   abc    "
        assert tester.call_meth_descr(str.center, "abc", (10, "-")) == "---abc----"
        assert tester.call_meth_descr(str.format, "{a}{b}{c}", tuple(), {"a": "a", "b": "b", "c": "c"}) == "abc"

        # create new methods from PyMethodDef
        m0 = tester.new_meth("abc", str.center)
        assert type(m0) is type(bin)
        assert m0(10) == "   abc    "
        assert m0(10, "-") == "---abc----"

        # str.lower is a unary builtin
        m1 = tester.new_meth("ABC", str.lower)
        assert type(m1) is type(bin)
        assert m1() == "abc"

        # str.splitlines is a binary builtin with an optional arg
        m2 = tester.new_meth("line0\nline1", str.splitlines)
        assert type(m2) is type(bin)
        assert m2() == ['line0', 'line1']
        assert m2(True) == ['line0\n', 'line1']
