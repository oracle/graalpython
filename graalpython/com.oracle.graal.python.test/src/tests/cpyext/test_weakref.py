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

import gc
import time
import unittest
import weakref

from . import CPyExtFunction, CPyExtType, assert_raises

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

GetRefHelper = CPyExtType(
    'GetRefHelper',
    '''
    static PyObject* getref(PyObject* unused, PyObject* ref) {
        if (ref == Py_None) {
            ref = NULL;
        }
        PyObject* obj = (PyObject*)0xDEADBEEF;
        int rc = PyWeakref_GetRef(ref, &obj);
        if (rc < 0) {
            assert(obj == NULL);
            return NULL;
        }
        if (obj == NULL) {
            return PyLong_FromLong(rc);
        }
        return Py_BuildValue("iN", rc, obj);
    }
    ''',
    tp_methods='{"getref", (PyCFunction)getref, METH_O | METH_STATIC, ""}'
)
getref_helper = GetRefHelper()

class TestWeakRef(unittest.TestCase):

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

    def test_PyWeakref_GetRef(self):
        class Foo:
            pass

        obj = Foo()
        ref = weakref.ref(obj)
        proxy = weakref.proxy(obj)
        rc, referent = getref_helper.getref(ref)
        self.assertEqual(rc, 1)
        self.assertIs(referent, obj)
        del referent
        rc, referent = getref_helper.getref(proxy)
        self.assertEqual(rc, 1)
        self.assertIs(referent, obj)
        del referent, obj

        for _ in range(3):
            gc.collect()
            if ref() is None:
                break
        self.assertIsNone(ref())
        self.assertEqual(getref_helper.getref(ref), 0)
        self.assertEqual(getref_helper.getref(proxy), 0)

        with self.assertRaisesRegex(TypeError, "^expected a weakref$"):
            getref_helper.getref(42)
        with self.assertRaises(SystemError):
            getref_helper.getref(None)

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

    def clear_ref_arguments():
        class Foo():
            pass
        f = Foo()
        log = []
        wr = weakref.ref(f, lambda wr: log.append("cb called"))
        return ((f, wr, log),)

    def clear_ref_equivalent(args):
        # Ignore args. There is no clearref in python, so
        # the equivalent is a weakref that never had a callback
        # in the first place
        class Foo():
            pass
        f = Foo()
        return weakref.ref(f), []

    def compare_cleared_weakref(cresult, presult):
        cwr, clog = cresult
        pwr, plog = presult
        for i in range(3):
            gc.collect()
            time.sleep(1)
        assert cwr() is None
        assert pwr() is None
        assert not clog
        assert not plog
        return True

    test_PyWeakref_ClearRef = CPyExtFunction(
        clear_ref_equivalent,
        clear_ref_arguments,
        code="""
        PyObject* wrap_PyWeakref_ClearRef(PyObject *o, PyObject* weakref, PyObject* log) {
            _PyWeakref_ClearRef((PyWeakReference *)weakref);
            return Py_BuildValue("OO", weakref, log);
        }
        """,
        argspec='OOO',
        arguments=["PyObject* o", "PyObject* weakref", "PyObject* log"],
        callfunction="wrap_PyWeakref_ClearRef",
        cmpfunc=compare_cleared_weakref,
    )
