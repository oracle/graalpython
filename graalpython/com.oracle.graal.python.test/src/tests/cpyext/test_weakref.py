# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import weakref

from . import CPyExtType, CPyExtTestCase, CPyExtFunction, GRAALPYTHON, unhandled_error_compare, assert_raises

__dir__ = __file__.rpartition("/")[0]

TestWeakRefHelper = CPyExtType(
    'TestWeakRefHelper',
    '''
    static PyObject* create_type(PyObject* unused, PyObject* args) {
        PyObject* bases;
        if (!PyArg_ParseTuple(args, "O", &bases))
            return NULL;
        PyType_Slot slots[] = {
            { 0 }
        };
        PyType_Spec spec = { "DynamicType", sizeof(PyObject), 0, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, slots };
        PyObject* result = PyType_FromSpecWithBases(&spec, bases);
        return result;
    }
    ''',
    tp_methods='{"create_type", (PyCFunction)create_type, METH_VARARGS | METH_STATIC, ""}'
)
helper = TestWeakRefHelper()

class TestWeakRef(object):
    
    def test_simple(self):
        class Foo:
            pass
        
        x = Foo()
        y = weakref.ref(x)
        assert type(y) == weakref.ReferenceType
        
    def test_native(self):
        clazz = helper.create_type((object,))
        x = clazz()
        assert_raises(TypeError, weakref.ref, x)

    # sometimes fails on CPython
    def ignored_test_native_sub(self):
        class Foo:
            pass
        clazz = helper.create_type((Foo,))
        x = clazz()
        y = weakref.ref(x)
        assert type(y) == weakref.ReferenceType

    # fails on CPython
    def ignored_test_native_sub2(self):
        class Foo:
            pass
        clazz = helper.create_type((Foo,))
        class Bar(clazz):
            pass
        x = Bar()
        y = weakref.ref(x)
        assert type(y) == weakref.ReferenceType
