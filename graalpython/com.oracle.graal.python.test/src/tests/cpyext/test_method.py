# Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import types
import unittest
import gc
import time

from . import CPyExtType, CPyExtTestCase, unhandled_error_compare, CPyExtFunction, assert_raises


def _reference_classmethod(args):
    if isinstance(args[0], type(list.append)):
        return classmethod(args[0])()
    raise TypeError


class TestMethod(unittest.TestCase):

    def test_methods(self):
        TestMethods = CPyExtType(
            "TestMethods",
            """
            static PyObject* notNull(PyObject *obj) {
                if (obj == NULL)
                    Py_RETURN_NONE;
                Py_INCREF(obj);
                return obj;
            }
            static PyObject* arrayToTuple(PyObject *const *args, Py_ssize_t nargs) {
                PyObject *argTuple = PyTuple_New(nargs);
                if (!argTuple)
                    return NULL;
                for (int i = 0; i < nargs; i++) {
                    Py_INCREF(args[i]);
                    PyTuple_SET_ITEM(argTuple, i, args[i]);
                }
                return argTuple;
            }
            static PyObject* keywordsToDict(PyObject *const *args, Py_ssize_t nargs, PyObject *kwnames) {
                if (kwnames == NULL)
                    Py_RETURN_NONE;
                PyObject *kwargs = PyDict_New();
                if (!kwargs)
                    return NULL;
                for (int i = 0; i < PyTuple_GET_SIZE(kwnames); i++) {
                    if (PyDict_SetItem(kwargs, PyTuple_GET_ITEM(kwnames, i), args[nargs + i]) < 0)
                        return NULL;
                }
                return kwargs;
            }
            static PyObject* meth_args(PyObject *self, PyObject *args) {
                return Py_BuildValue("NN", notNull(self), notNull(args));
            }
            static PyObject* meth_kwargs(PyObject *self, PyObject *args, PyObject *kwargs) {
                return Py_BuildValue("NNN", notNull(self), notNull(args), notNull(kwargs));
            }
            static PyObject* meth_fastcall(PyObject *self, PyObject *const *args, Py_ssize_t nargs) {
                return Py_BuildValue("NN", notNull(self), arrayToTuple(args, nargs));
            }
            static PyObject* meth_fastcall_keywords(PyObject *self, PyObject *const *args, Py_ssize_t nargs, PyObject *kwnames) {
                return Py_BuildValue("NNN", notNull(self), arrayToTuple(args, nargs), keywordsToDict(args, nargs, kwnames));
            }
            static PyObject* meth_method(PyObject *self, PyObject *cls, PyObject *const *args, Py_ssize_t nargs, PyObject *kwnames) {
                return Py_BuildValue("NNNN", notNull(self), notNull(cls), arrayToTuple(args, nargs), keywordsToDict(args, nargs, kwnames));
            }
            """,
            tp_methods="""
            // instance methods
            {"meth_o", meth_args, METH_O, ""},
            {"meth_noargs", meth_args, METH_NOARGS, ""},
            {"meth_varargs", meth_args, METH_VARARGS, ""},
            {"meth_varargs_keywords", (PyCFunction)meth_kwargs, METH_VARARGS | METH_KEYWORDS, ""},
            {"meth_fastcall", (PyCFunction)meth_fastcall, METH_FASTCALL, ""},
            {"meth_fastcall_keywords", (PyCFunction)meth_fastcall_keywords, METH_FASTCALL | METH_KEYWORDS, ""},
            {"meth_method", (PyCFunction)meth_method, METH_FASTCALL | METH_KEYWORDS | METH_METHOD, ""},
            // class methods
            {"meth_class_o", meth_args, METH_CLASS | METH_O, ""},
            {"meth_class_noargs", meth_args, METH_CLASS | METH_NOARGS, ""},
            {"meth_class_varargs", meth_args, METH_CLASS | METH_VARARGS, ""},
            {"meth_class_varargs_keywords", (PyCFunction)meth_kwargs, METH_CLASS | METH_VARARGS | METH_KEYWORDS, ""},
            {"meth_class_fastcall", (PyCFunction)meth_fastcall, METH_CLASS | METH_FASTCALL, ""},
            {"meth_class_fastcall_keywords", (PyCFunction)meth_fastcall_keywords, METH_CLASS | METH_FASTCALL | METH_KEYWORDS, ""},
            {"meth_class_method", (PyCFunction)meth_method, METH_CLASS | METH_FASTCALL | METH_KEYWORDS | METH_METHOD, ""},
            // static methods
            {"meth_static_o", meth_args, METH_STATIC | METH_O, ""},
            {"meth_static_noargs", meth_args, METH_STATIC | METH_NOARGS, ""},
            {"meth_static_varargs", meth_args, METH_STATIC | METH_VARARGS, ""},
            {"meth_static_varargs_keywords", (PyCFunction)meth_kwargs, METH_STATIC | METH_VARARGS | METH_KEYWORDS, ""},
            {"meth_static_fastcall", (PyCFunction)meth_fastcall, METH_STATIC | METH_FASTCALL, ""},
            {"meth_static_fastcall_keywords", (PyCFunction)meth_fastcall_keywords, METH_STATIC | METH_FASTCALL | METH_KEYWORDS, ""}
            """,
        )
        
        class TestMethodsSubclass(TestMethods):
            pass

        obj = TestMethodsSubclass()
        # instance methods
        assert obj.meth_o(1) == (obj, 1)
        assert obj.meth_noargs() == (obj, None)
        assert obj.meth_varargs(1, 2, 3) == (obj, (1, 2, 3))
        assert obj.meth_varargs_keywords(1, 2, 3, a=1, b=2) == (obj, (1, 2, 3), {'a': 1, 'b': 2})
        assert obj.meth_fastcall(1, 2, 3) == (obj, (1, 2, 3))
        assert obj.meth_fastcall_keywords(1, 2, 3, a=1, b=2) == (obj, (1, 2, 3), {'a': 1, 'b': 2})
        assert obj.meth_method(1, 2, 3, a=1, b=2) == (obj, TestMethods, (1, 2, 3), {'a': 1, 'b': 2})
        # class methods
        assert obj.meth_class_o(1) == (TestMethodsSubclass, 1)
        assert obj.meth_class_noargs() == (TestMethodsSubclass, None)
        assert obj.meth_class_varargs(1, 2, 3) == (TestMethodsSubclass, (1, 2, 3))
        assert obj.meth_class_varargs_keywords(1, 2, 3, a=1, b=2) == (TestMethodsSubclass, (1, 2, 3), {'a': 1, 'b': 2})
        assert obj.meth_class_fastcall(1, 2, 3) == (TestMethodsSubclass, (1, 2, 3))
        assert obj.meth_class_fastcall_keywords(1, 2, 3, a=1, b=2) == (TestMethodsSubclass, (1, 2, 3), {'a': 1, 'b': 2})
        assert obj.meth_class_method(1, 2, 3, a=1, b=2) == (TestMethodsSubclass, TestMethods, (1, 2, 3), {'a': 1, 'b': 2})
        # static methods
        assert obj.meth_static_o(1) == (None, 1)
        assert obj.meth_static_noargs() == (None, None)
        assert obj.meth_static_varargs(1, 2, 3) == (None, (1, 2, 3))
        assert obj.meth_static_varargs_keywords(1, 2, 3, a=1, b=2) == (None, (1, 2, 3), {'a': 1, 'b': 2})
        assert obj.meth_static_fastcall(1, 2, 3) == (None, (1, 2, 3))
        assert obj.meth_static_fastcall_keywords(1, 2, 3, a=1, b=2) == (None, (1, 2, 3), {'a': 1, 'b': 2})

        assert_raises(TypeError, obj.meth_noargs, 1)
        assert_raises(TypeError, obj.meth_o)
        assert_raises(TypeError, obj.meth_o, 1, 2)
        assert_raises(TypeError, obj.meth_varargs, 1, 2, a=1)
        assert_raises(TypeError, obj.meth_fastcall, 1, 2, a=1)
        assert_raises(TypeError, obj.meth_class_noargs, 1)
        assert_raises(TypeError, obj.meth_class_o)
        assert_raises(TypeError, obj.meth_class_o, 1, 2)
        assert_raises(TypeError, obj.meth_class_varargs, 1, 2, a=1)
        assert_raises(TypeError, obj.meth_class_fastcall, 1, 2, a=1)
        assert_raises(TypeError, obj.meth_static_noargs, 1)
        assert_raises(TypeError, obj.meth_static_o)
        assert_raises(TypeError, obj.meth_static_o, 1, 2)
        assert_raises(TypeError, obj.meth_static_varargs, 1, 2, a=1)
        assert_raises(TypeError, obj.meth_static_fastcall, 1, 2, a=1)


    def test_meth_varargs_with_escaping_args_tuple(self):
        TestEscapingArgsTuple = CPyExtType(
            "TestEscapingArgsTuple",
            """
            static int init(PyObject *selfObj, PyObject *args, PyObject *kwargs) {
                TestEscapingArgsTupleObject *self = (TestEscapingArgsTupleObject *) selfObj;
                if (PyArg_ParseTuple(args, "OO", &self->container, &self->appender) == 0) {
                    return -1;
                }
                Py_INCREF(self->container);
                self->object = NULL;
                return 0;
            }
            
            static int force_native_storage(PyObject *args) {
                PyObject *first = PyTuple_GET_ITEM(args, 0);
                if (!first) {
                    PyErr_SetString(PyExc_ValueError, "first item must not be null");
                    return -1;
                }
                return 0;
            }
            
            static PyObject* hold(TestEscapingArgsTupleObject *self, PyObject *args) {
                if (force_native_storage(args) == -1) {
                    return NULL;
                }
                Py_XSETREF(self->object, Py_NewRef(args));
                Py_RETURN_NONE;
            }
            
            static PyObject* steal(TestEscapingArgsTupleObject *self, PyObject *args) {
                if (force_native_storage(args) == -1) {
                    return NULL;
                }
                Py_INCREF(args);
                PyList_SetItem(self->container, 0, args);
                Py_RETURN_NONE;
            }
            
            static PyObject* give_to_managed(TestEscapingArgsTupleObject *self, PyObject *args) {
                if (force_native_storage(args) == -1) {
                    return NULL;
                }
                self->stolen = args;
                self->stolen_element = PyTuple_GET_ITEM(args, 0);
                return PyObject_CallOneArg(self->appender, args);
            }
            
            static PyObject* recursive(TestEscapingArgsTupleObject *self, PyObject *args) {
                if (force_native_storage(args) == -1) {
                    return NULL;
                }
                Py_ssize_t nargs = PyTuple_GET_SIZE(args);
                if (nargs > 1) {
                    return PyLong_FromSsize_t(nargs);
                }
                return PyObject_CallNoArgs(PyTuple_GET_ITEM(args, 0));
            }
            """,
            cmembers="""
            PyObject *container;
            PyObject *appender;
            PyObject *object;
            PyObject *stolen;
            PyObject *stolen_element;
            """,
            tp_methods="""
            {"hold", _PyCFunction_CAST(hold), METH_VARARGS, ""},
            {"steal", _PyCFunction_CAST(steal), METH_VARARGS, ""},
            {"give_to_managed", _PyCFunction_CAST(give_to_managed), METH_VARARGS, ""},
            {"recursive", _PyCFunction_CAST(recursive), METH_VARARGS, ""}
            """,
            tp_members='''
            {"object", T_OBJECT, offsetof(TestEscapingArgsTupleObject, object), 0, NULL},
            {"stolen", T_OBJECT, offsetof(TestEscapingArgsTupleObject, stolen), 0, NULL},
            {"stolen_element", T_OBJECT, offsetof(TestEscapingArgsTupleObject, stolen_element), 0, NULL}
            ''',
            tp_basicsize="sizeof(TestEscapingArgsTupleObject)",
            tp_new="PyType_GenericNew",
            tp_init="init",
        )

        appender_list = []
        def append_to_list(item):
            appender_list.append(item)
            return None

        def recursive_call():
            return tester.recursive('x', 'y', 'z')

        container = [None]
        tester = TestEscapingArgsTuple(container, append_to_list)

        # the first time, the args tuple will be passed with a managed storage
        tester.hold("hello", "world")
        tester.steal(1, 2, 3)
        assert container[0] == (1, 2, 3)
        tester.give_to_managed('a', 'b', 'c')
        assert appender_list[0] == ('a', 'b', 'c')
        recursive_result = tester.recursive(recursive_call)
        assert recursive_result == 3

        # the second time, the args tuple will be passed with native storage
        tester.hold("hello", "beautiful", "world")
        tester.steal(4, 5, 6)
        tester.give_to_managed('d', 'e', 'f')

        for _ in range(3):
            gc.collect()
            time.sleep(0.5)

        assert tester.object == ("hello", "beautiful", "world")
        assert container[0] == (4, 5, 6)
        assert appender_list[1] == ('d', 'e', 'f')
        assert tester.stolen == ('d', 'e', 'f')
        assert tester.stolen_element == 'd'
        assert tester.stolen[0] is tester.stolen_element


class TestPyMethod(CPyExtTestCase):

    test_PyMethod_New = CPyExtFunction(
        lambda args: types.MethodType(*args),
        lambda: (
            (list.append, str),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* func", "PyObject* self"],
        cmpfunc=unhandled_error_compare
    )
