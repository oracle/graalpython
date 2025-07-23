# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import builtins
import os
import pathlib
import sys
import unittest

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare, CPyExtType

__global_builtins_dict = builtins.__dict__


def _reference_importmodule(args):
    return __import__(args[0], fromlist=["*"])


def _reference_format_float(args):
    val, format_spec, prec = args
    if format_spec == b'r':
        return repr(val)
    return float(val).__format__("." + str(prec) + format_spec.decode())


def _reference_builtins(args):
    return type(__global_builtins_dict)


class TestMisc(CPyExtTestCase):

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

    test_PyImport_GetModule = CPyExtFunction(
        lambda args: sys.modules.get(args[0]),
        lambda: (
            ("os",),
        ),
        resultspec="O",
        argspec="O",
        arguments=["PyObject* name"],
        cmpfunc=unhandled_error_compare
    )

    ignored_test_PointerEquality_Primitive = CPyExtFunction(
        lambda args: True,
        lambda: (
            (True, lambda arg0, *args: arg0),
            (False, lambda arg0, *args: arg0),
            (1000, lambda arg0, *args: arg0),
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

    # Tests if wrapped Java primitive values do not share the same
    # native pointer.
    test_primitive_sharing = CPyExtFunction(
        lambda args: True,
        lambda: (
            (123.0, ),
        ),
        code="""
        #ifdef GRAALVM_PYTHON
        // internal function defined in 'capi.c'
        int GraalPyPrivate_ToNative(void *);
        #else
        // nothing to do on CPython
        static inline int GraalPyPrivate_ToNative(void *arg) {
            return 0;
        }
        #endif

        PyObject* primitive_sharing(PyObject* val) {
            Py_ssize_t val_refcnt = Py_REFCNT(val);
            // assume val's refcnt is X > 0
            Py_INCREF(val);
            // val's refcnt should now be X+1

            double dval = PyFloat_AsDouble(val);

            if (GraalPyPrivate_ToNative(val)) {
                return Py_False;
            }

            // a fresh object with the same value
            PyObject *val1 = PyFloat_FromDouble(dval);

            if (GraalPyPrivate_ToNative(val1)) {
                return Py_False;
            }

            // now, kill it
            Py_DECREF(val1);

            // reset val's refcnt to X
            Py_DECREF(val);

            return val_refcnt == Py_REFCNT(val) ? Py_True : Py_False;
        }
        """,
        resultspec="O",
        argspec="O",
        arguments=["PyObject* val"],
        cmpfunc=unhandled_error_compare
    )

    test_PyOS_double_to_string = CPyExtFunction(
        _reference_format_float,
        lambda: (
            (1.212, b"f", 2),
            (float('nan'), b"f", 2),
            (1.23456789, b"f", 2),
            (123.456789, b"f", 6),
            (123.456721, b"e", 6),
            (123.456789, b"r", 0),
        ),
        code="""
        char* wrap_PyOS_double_to_string(double val, char format, int prec) {
            return PyOS_double_to_string(val, format, prec, 0, NULL);
        }
        """,
        resultspec="s",
        argspec="dci",
        arguments=["double val", "char format", "int prec"],
        callfunction="wrap_PyOS_double_to_string",
        cmpfunc=unhandled_error_compare
    )

    test_PyEval_GetBuiltins = CPyExtFunction(
        _reference_builtins,
        lambda: (
            tuple(),
        ),
        code="""
        PyObject* wrap_PyEval_GetBuiltins(void) {
            return (PyObject *) Py_TYPE(PyEval_GetBuiltins());
        }
        """,
        resultspec="O",
        argspec="",
        arguments=[],
        callfunction="wrap_PyEval_GetBuiltins",
        cmpfunc=unhandled_error_compare
    )

    test_PyEval_GetFrame = CPyExtFunction(
        lambda args: sys._getframe(1),
        lambda: ((),),
        code="""
        PyObject* wrap_PyEval_GetFrame(void) {
            return (PyObject*)PyEval_GetFrame();
        }
        """,
        resultspec="O",
        argspec="",
        arguments=[],
        callfunction="wrap_PyEval_GetFrame",
        cmpfunc=unhandled_error_compare,
    )

    test_PyOS_FSPath = CPyExtFunction(
        lambda args: os.fspath(*args),
        lambda: (
            (b"bytespath",),
            ("stringpath",),
            (pathlib.Path("pathpath"),),
            (123,),
            (object(),),
        ),
        callfunction="call_PyOS_FSPath",
        code="""
        static PyObject* call_PyOS_FSPath(PyObject* value) {
            return PyOS_FSPath(value);
        }
        """,
        resultspec="O",
        argspec="O",
        arguments=["PyObject* value"],
        cmpfunc=unhandled_error_compare
    )


@unittest.skipUnless(sys.implementation.name == 'graalpy', "GraalPy-only")
def test_graalpy_version():
    tester = CPyExtType(
        "VersionTester",
        code='''
        static PyObject* get_version_str(PyObject* unused) {
            return PyUnicode_FromString(GRAALPY_VERSION);
        }
        static PyObject* get_version_num(PyObject* unused) {
            return PyLong_FromLong(GRAALPY_VERSION_NUM);
        }
        ''',
        tp_methods='''
        {"get_version_str", (PyCFunction)get_version_str, METH_NOARGS | METH_STATIC, ""},
        {"get_version_num", (PyCFunction)get_version_num, METH_NOARGS | METH_STATIC, ""}
        ''',
    )
    version = sys.implementation.version
    assert tester.get_version_str() == f'{version.major}.{version.minor}.{version.micro}'
    assert tester.get_version_num() == sys.implementation.hexversion
    # This is an anti-backport trap. The commit that changed the hexversion format should not be backported because
    # existing projects might already contain tests for the next feature release in the old format (pybind11 does)
    assert version.major >= 25


def test_unicode_docstring():
    class ClassWithDoc:
        """This class has a doc 🙂"""

    tester = CPyExtType(
        "DocstringTester",
        code='''
        static PyObject* get_doc(PyObject* unused, PyObject* type) {
            return PyUnicode_FromString(((PyTypeObject*)type)->tp_doc);
        }
        ''',
        tp_methods='''
        {"get_doc", (PyCFunction)get_doc, METH_O | METH_STATIC, ""}
        ''',
    )
    assert tester.get_doc(ClassWithDoc)
