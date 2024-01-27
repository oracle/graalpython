# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
from unittest import skipIf
from . import CPyExtType


if sys.implementation.name == "graalpy":
    import polyglot
    from __graalpython__ import is_native
    is_windows = sys.platform == "win32"

    class TestPyStructNumericSequenceTypes(object):

        @skipIf(is_native, "not supported in native mode")
        def test_interop_assertions(self):
            class MyType(object):
                data = 100000000000

            t = MyType()
            import java
            BigInteger = java.type("java.math.BigInteger")

            polyglot.register_interop_behavior(MyType,
                                               is_number=False,
                                               as_long=lambda x: x.data)
            try:
                # since is number is false, this should throw a TypeError
                print(BigInteger.valueOf(t))
            except TypeError:
                assert True
            else:
                assert False

            polyglot.register_interop_behavior(MyType,
                                               is_number=True,
                                               fits_in_long=lambda x: True)
            try:
                # since as_long is not defined, this should throw a TypeError
                print(BigInteger.valueOf(t))
            except TypeError:
                assert True
            else:
                assert False

        @skipIf(is_native, "not supported in native mode")
        def test_native_object_interop_behavior_extension(self):
            MyNativeType = CPyExtType("MyNativeType",
                                 '''static PyObject* mynative_type_new(PyTypeObject* cls, PyObject* a, PyObject* b) {
                                     PyObject* obj;
                                     MyNativeTypeObject* typedObj;
                                     obj = PyBaseObject_Type.tp_new(cls, a, b);
                                     
                                     typedObj = ((MyNativeTypeObject*)obj);
                                     typedObj->data = PyLong_FromLong(10);
                                     Py_XINCREF(obj);
                                     return obj;
                                }
                                
                                static PyObject* get_data(PyObject* self) {
                                    MyNativeTypeObject* typedObj;
                                    typedObj = ((MyNativeTypeObject*)self);
                                    
                                    Py_INCREF(typedObj->data);
                                    return typedObj->data;
                                }
                                ''',
                                cmembers="PyObject* data;",
                                tp_new="mynative_type_new",
                                tp_methods='{"get_data", (PyCFunction)get_data, METH_NOARGS, ""}')

            instance = MyNativeType()
            assert instance.get_data() == 10

            import java
            BigInteger = java.type("java.math.BigInteger")

            try:
                BigInteger.valueOf(instance)
            except Exception as e:
                assert True
            else:
                assert False, "should throw an error"

            polyglot.register_interop_behavior(MyNativeType, is_number=True, fits_in_long=lambda t: True,
                                               as_long=lambda t: t.get_data())
            defined = polyglot.get_registered_interop_behavior(MyNativeType)
            for method in ['is_number', 'fits_in_long', 'as_long']:
                assert method in defined, f"method: {method} not found in defined methods: {defined}"
            try:
                bi = BigInteger.valueOf(instance)
                assert int(bi) == instance.get_data()
            except Exception as e:
                # print("Error : ", e)
                assert False

        @skipIf(is_windows, "GR-51663: fails on windows")
        def test_native_sequence_interop(self):
            MySequenceNativeType = CPyExtType("MySequenceNativeType",
                                """
                                static PyObject* mynative_seq_type_new(PyTypeObject* cls, PyObject* a, PyObject* b) {
                                     PyObject* obj;
                                     MySequenceNativeTypeObject* typedObj;
                                     obj = PyBaseObject_Type.tp_new(cls, a, b);
                                     
                                     typedObj = ((MySequenceNativeTypeObject*)obj);
                                     // data = [0,1,2,3,4]
                                     typedObj->data = PyList_New(5);
                                     Py_INCREF(typedObj->data);
                                     PyList_SetItem(typedObj->data, 0, PyLong_FromLong(0));
                                     PyList_SetItem(typedObj->data, 1, PyLong_FromLong(1));
                                     PyList_SetItem(typedObj->data, 2, PyLong_FromLong(2));
                                     PyList_SetItem(typedObj->data, 3, PyLong_FromLong(3));
                                     PyList_SetItem(typedObj->data, 4, PyLong_FromLong(4));
                                     Py_XINCREF(obj);
                                     return obj;
                                }
                            
                                static Py_ssize_t mynative_seq_type_sq_length(PyObject* obj) {
                                    MySequenceNativeTypeObject* typedObj;
                                    typedObj = ((MySequenceNativeTypeObject*)obj);
                                    
                                    return PyList_Size(typedObj->data);
                                }
                                
                                static PyObject* get_data(PyObject* self) {
                                    MySequenceNativeTypeObject* typedObj;
                                    typedObj = ((MySequenceNativeTypeObject*)self);
                                    
                                    Py_INCREF(typedObj->data);
                                    return typedObj->data;
                                }
                                
                                static PyObject* mynative_seq_type_sq_item(PyObject *self, Py_ssize_t i) {
                                    MySequenceNativeTypeObject* typedObj;
                                    typedObj = ((MySequenceNativeTypeObject*)self);
                                    
                                    PyObject* item = PyList_GetItem(typedObj->data, i);
                                    Py_INCREF(item);
                                    return item;
                                }
                                
                                static int mynative_seq_type_sq_ass_item(PyObject *self, Py_ssize_t i, PyObject *v) {
                                    MySequenceNativeTypeObject* typedObj;
                                    typedObj = ((MySequenceNativeTypeObject*)self);
                                    
                                    Py_ssize_t len = PyList_Size(typedObj->data);
                                    if (i == len) {
                                        return PyList_Insert(typedObj->data, i, v);
                                    } else {
                                        if (v == NULL) {
                                            return PyList_SetSlice(typedObj->data, i, i+1, v);
                                        } else { 
                                            return PyList_SetItem(typedObj->data, i, v);
                                        }
                                    }
                                }
                                """,
                                cmembers="PyObject* data;",
                                tp_new="mynative_seq_type_new",
                                sq_length="mynative_seq_type_sq_length",
                                sq_ass_item="mynative_seq_type_sq_ass_item",
                                sq_item="mynative_seq_type_sq_item",
                                tp_methods='{"get_data", (PyCFunction)get_data, METH_NOARGS, ""}')

            t = MySequenceNativeType()
            import polyglot

            assert polyglot.__has_size__(t)
            assert 5 == polyglot.__get_size__(t)
            assert 1 == polyglot.__read__(t, 1)
            # remove - [1,2,3,4]
            polyglot.__remove__(t, 0)
            assert 4 == polyglot.__get_size__(t)
            assert 2 == polyglot.__read__(t, 1)
            # append - [1,2,3,4,5]
            polyglot.__write__(t, 4, 5)
            assert 5 == polyglot.__get_size__(t)
            assert 5 == polyglot.__read__(t, 4)
            # edit - [1,20,3,4,5]
            polyglot.__write__(t, 1, 20)
            assert 5 == polyglot.__get_size__(t)
            assert 20 == polyglot.__read__(t, 1)
