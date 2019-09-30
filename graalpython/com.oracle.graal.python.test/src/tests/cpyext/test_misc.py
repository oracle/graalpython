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


def _reference_importmodule(args):
    return __import__(args[0], fromlist=["*"])


class TestMisc(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestMisc, self).compile_module(name)

    test_PyEllipsis_isSingleton = CPyExtFunction(
        lambda args: 1,
        lambda: (
            (...,),
        ),
        callfunction="CheckPyEllipsis",
        code="""
        static int CheckPyEllipsis(PyObject* ellipsis_singleton) {
            return ellipsis_singleton == &_Py_EllipsisObject;
        }
        """,
        resultspec="i",
        argspec="O",
        arguments=["PyObject* ellipsis_singleton"],
    )

    test_PyEllipsis_type = CPyExtFunction(
        lambda args: 1,
        lambda: (
            (...,),
        ),
        callfunction="CheckPyEllipsisType",
        code="""
        static int CheckPyEllipsisType(PyObject* ellipsis_singleton) {
            return Py_TYPE(&_Py_EllipsisObject) == &PyEllipsis_Type;
        }
        """,
        resultspec="i",
        argspec="O",
        arguments=["PyObject* ellipsis_singleton"],
    )

    test_PyImport_ImportModule = CPyExtFunction(
        _reference_importmodule,
        lambda: (
            ("os",),
            ("os.path",),
            ("distutils.core",),
            ("nonexisting",),
        ),
        resultspec="O",
        argspec="s",
        arguments=["char* module_name"],
        cmpfunc=unhandled_error_compare
    )

    test_PyImport_GetModuleDict = CPyExtFunction(
        lambda args: sys.modules,
        lambda: (
            tuple(),
        ),
        code='''PyObject* wrap_PyImport_GetModuleDict(PyObject* ignored) {
            return PyImport_GetModuleDict();
        }
        ''',
        resultspec="O",
        argspec="",
        arguments=["PyObject* ignored"],
        callfunction="wrap_PyImport_GetModuleDict",
    )

    test_PyImport_Import = CPyExtFunction(
        _reference_importmodule,
        lambda: (
            ("os",),
            ("os.path",),
            ("distutils.core",),
            ("nonexisting",),
        ),
        resultspec="O",
        argspec="O",
        arguments=["PyObject* module_name"],
        cmpfunc=unhandled_error_compare
    )

    test_PyImport_ImportModuleLevelObject = CPyExtFunction(
        lambda args: __import__(*args),
        lambda: (
            ("os", None, None, ["*"], 0),
            ("os", None, None, ["path"], 0),
        ),
        resultspec="O",
        argspec="OOOOi",
        arguments=["PyObject* name", "PyObject* globals", "PyObject* locals", "PyObject* fromlist", "int level"],
        cmpfunc=unhandled_error_compare
    )

    test_PyImport_ImportModuleLevel = CPyExtFunction(
        lambda args: __import__(*args),
        lambda: (
            ("os", None, None, ["*"], 0),
            ("os", None, None, ["path"], 0),
        ),
        resultspec="O",
        argspec="sOOOi",
        arguments=["char* name", "PyObject* globals", "PyObject* locals", "PyObject* fromlist", "int level"],
        cmpfunc=unhandled_error_compare
    )

    test_PyTruffle_Intrinsic_Pmovmskb = CPyExtFunction(
        lambda args: True,
        lambda: (
            (0xffffcafebabe, 0xefffdeadbeef),
        ),
        code="""
        #include <emmintrin.h>
        PyObject* PyTruffle_Intrinsic_Pmovmskb(PyObject* arg0, PyObject* arg1) {
            int r;
            int64_t a = (int64_t) PyLong_AsSsize_t(arg0);
            int64_t b = (int64_t) PyLong_AsSsize_t(arg1);
            __m128i zero = _mm_setzero_si128();
            __m128i v = _mm_set_epi64(_m_from_int64(b), _m_from_int64(a));
            v = _mm_cmpeq_epi8(v, zero);
            r = _mm_movemask_epi8(v);
            return (r == 0 || r == 49344) ? Py_True : Py_False;
        }
        """,
        resultspec="O",
        argspec="OO",
        arguments=["PyObject* arg0", "PyObject* arg1"],
        cmpfunc=unhandled_error_compare
    )

    test_PointerEquality_Primitive = CPyExtFunction(
        lambda args: True,
        lambda: (
            (True, lambda arg0, *args: arg0),
            (False, lambda arg0, *args: arg0),
            (10, lambda arg0, *args: arg0),
            (10.0, lambda arg0, *args: arg0),
            (float('nan'), lambda arg0, *args: arg0),
            ("ten", lambda arg0, *args: arg0),
        ),
        code="""PyObject* PointerEquality_Primitive(PyObject* pyVal, PyObject* fun) {
            PyObject** dummyArray = (PyObject**) malloc(sizeof(PyObject*));
            PyObject *arg, *result0;
            Py_INCREF(pyVal);
            Py_INCREF(fun);
            dummyArray[0] = pyVal;

            arg = PyTuple_New(1);
            PyTuple_SET_ITEM(arg, 0, dummyArray[0]);
            Py_INCREF(arg);
            result0 = PyObject_Call(fun, arg, NULL);
            if (pyVal != result0) {
                PyErr_Format(PyExc_ValueError, "%s is not pointer equal: 0x%lx vs. 0x%lx", PyUnicode_AsUTF8(PyObject_Repr(pyVal)), (void*)pyVal, (void*)result0);
                return NULL;
            }

            free(dummyArray);
            Py_DECREF(pyVal);
            Py_DECREF(fun);
            return Py_True;
        }
        """,
        resultspec="O",
        argspec="OO",
        arguments=["PyObject* pyVal", "PyObject* fun"],
        cmpfunc=unhandled_error_compare
    )
