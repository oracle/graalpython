# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtType, CPyExtTestCase, CPyExtFunction, GRAALPYTHON, unhandled_error_compare

__dir__ = __file__.rpartition("/")[0]


class TestMixedInheritanceDict:
    def test_base_type(self):
        _A = CPyExtType("_A",
                        '''static PyObject* _A_new(PyTypeObject* cls, PyObject* a, PyObject* b) {
                             PyObject* obj;
                             _AObject* typedObj;
                             obj = PyBaseObject_Type.tp_new(cls, a, b);

                             typedObj = ((_AObject*)obj);
                             typedObj->a = 1;
                             Py_INCREF(Py_None);
                             Py_XINCREF(obj);
                             return obj;
                        }
                         ''',
                        cmembers="int a;",
                        tp_new="_A_new")

        class A:
            __slots__ = ()

        class B(_A, A):
            b = 2

        class C(B):
            __slots__ = ()
            c = 3

        assert not hasattr(_A(), '__dict__')
        assert not hasattr(A(), '__dict__')
        assert hasattr(B(), '__dict__')
        assert hasattr(C(), '__dict__')

    def test_slots_subprop(self):
        class PropertySubSlots(property):
            __slots__ = ()

        try:
            class Foo(object):
                @PropertySubSlots
                def spam(self):
                    """Trying to copy this docstring will raise an exception"""
                    return 1
        except AttributeError:
            pass
        else:
            raise Exception("AttributeError not raised")

    def test_mixed_inheritance_with_length(self):
        TestMappingSize = CPyExtType("TestMappingSize",
                                     """
                                     Py_ssize_t test_mp_length(PyObject* a) {
                                         return 11;
                                     }
                                     PyObject* callSqSize(PyObject* self, PyObject* arg) {
                                         Py_ssize_t res = PySequence_Size(arg);
                                         if (PyErr_Occurred()) {
                                             return NULL;
                                         }
                                         return PyLong_FromSsize_t(res);
                                     }
                                     PyObject* callMpSize(PyObject* self, PyObject* arg) {
                                         Py_ssize_t res = PyMapping_Size(arg);
                                         if (PyErr_Occurred()) {
                                             return NULL;
                                         }
                                         return PyLong_FromSsize_t(res);
                                     }
                                     """,
                                     tp_methods='''
                                     {"callSqSize", (PyCFunction)callSqSize, METH_O, ""},
                                     {"callMpSize", (PyCFunction)callMpSize, METH_O, ""}
                                     ''',
                                     mp_length="&test_mp_length",
        )
        tester = TestMappingSize()
        try:
            tester.callSqSize(tester)
        except TypeError as e:
            assert "not a sequence" in repr(e)
        else:
            assert False

        class B:
            pass

        class B2:
            def __len__(self) -> int: ...

        class C(TestMappingSize, B):
            pass

        assert tester.callSqSize(C()) == 11

        class C(TestMappingSize, B2):
            pass

        assert tester.callSqSize(C()) == 11

        class B3:
            def __len__(self):
                return 128

        class C(B3, TestMappingSize):
            pass

        assert tester.callSqSize(C()) == 128
        assert tester.callMpSize(C()) == 128
