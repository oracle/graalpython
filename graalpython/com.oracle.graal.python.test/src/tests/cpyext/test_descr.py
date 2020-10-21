# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare 
__dir__ = __file__.rpartition("/")[0]


def _reference_classmethod(args):
    if isinstance(args[0], type(list.append)):
        return classmethod(args[0])()
    raise TypeError

class TestDescr(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestDescr, self).compile_module(name)

    test_PyDescr_NewClassMethod = CPyExtFunction(
        _reference_classmethod,
        lambda: (
            (type.__repr__,),
            (lambda x: x,),
        ),
        code="""
        static PyObject* wrap_PyDescr_NewClassMethod(PyObject* method) {
            PyTypeObject *methoddescr_type = NULL;
            PyObject *meth = PyObject_GetAttrString((PyObject*)&PyList_Type, "append");
            if (!meth) return NULL;
            methoddescr_type = Py_TYPE(meth);
            Py_DECREF(meth);
            if (PyObject_TypeCheck(method, (PyTypeObject *)methoddescr_type)) {
                PyMethodDescrObject *descr = (PyMethodDescrObject *)method;
                PyTypeObject *d_type = descr->d_common.d_type;
                PyObject *class_method = PyDescr_NewClassMethod(d_type, descr->d_method);
                PyObject *args = PyTuple_New(0);
                Py_INCREF(args);
                PyObject *result = PyObject_CallObject(class_method, args);
                Py_DECREF(args); Py_DECREF(args);
                Py_DECREF(class_method);
                return result;
            }
            PyErr_SetString(PyExc_TypeError, "Class-level classmethod() can only be called on a method_descriptor or instance method.");
            return NULL;
        }
        """,
        resultspec="O",
        argspec="O",
        arguments=["PyObject* method"],
        callfunction="wrap_PyDescr_NewClassMethod",
        cmpfunc=unhandled_error_compare
    )
