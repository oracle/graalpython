# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

    class TestPyStructNumericSequenceTypes(object):

        @skipIf(is_native, "not supported in native mode")
        def test_host_interop_extension(self):
            MyNativeType = CPyExtType("MyNativeType",
                                 '''static PyObject* mymativetype_new(PyTypeObject* cls, PyObject* a, PyObject* b) {
                                     PyObject* obj;
                                     MyNativeTypeObject* typedObj;
                                     obj = PyBaseObject_Type.tp_new(cls, a, b);
                                     
                                     typedObj = ((MyNativeTypeObject*)obj);
                                     typedObj->data = PyLong_FromLong(10);
                                     Py_XINCREF(obj);
                                     return obj;
                                }
                                static PyObject* get_data(PyObject* self) {
                                    return ((MyNativeTypeObject*)self)->data;
                                }
                                ''',
                                cmembers="PyObject* data;",
                                tp_new="mymativetype_new",
                                tp_methods='{"get_data", (PyCFunction)get_data, METH_NOARGS, ""}')

            instance = MyNativeType()
            assert instance.get_data() == 10

            import polyglot
            import java
            BigInteger = java.type("java.math.BigInteger")

            try:
                BigInteger.valueOf(instance)
            except Exception as e:
                assert True
            else:
                assert False, "should throw an error"

            polyglot.register_host_interop_behavior(MyNativeType,
                                                    is_number=True,
                                                    fits_in_long=lambda t: True,
                                                    as_long=lambda t: t.get_data())
            # print("defined : ", polyglot.guar(TestPyStructNumericSequence))
            try:
                bi = BigInteger.valueOf(instance)
                assert int(bi) == instance.get_data()
            except Exception as e:
                # print("Error : ", e)
                assert False
