# Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare

__dir__ = __file__.rpartition("/")[0]


# python_run_test_result = None

def _reference_run_string(args):
    if not isinstance(args[2], dict):
        if sys.version_info.minor >= 6:
            raise SystemError
        else:
            raise TypeError
    if not isinstance(args[3], dict):
        raise TypeError
    return None


def _run_string_compare(x, y):
    res = unhandled_error_compare(x, y)
    if (isinstance(x, Exception)):
        return res

    global python_run_test_result
    pr = python_run_test_result
    res = res and pr == 42
    python_run_test_result = None
    if not res:
        assert False, "python_run_test_result is %s" % pr
    return res


class TestPythonRun(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPythonRun, self).compile_module(name)

    test_PyRun_StringFlags = CPyExtFunction(
        _reference_run_string,
        lambda: (
            ("globals().update({'python_run_test_result':42})", 256, globals(), locals(), 0),
            ("globals().update({'python_run_test_result':42})", 256, 'globals()', locals(), 0),
            ("globals().update({'python_run_test_result':42})", 256, globals(), 'locals()', 0),
        ),
        resultspec="O",
        argspec='siOOk',
        arguments=["char* source", "int type", "PyObject* globals", "PyObject* locals", "PyCompilerFlags* ignored"],
        cmpfunc=unhandled_error_compare
    )

    test_Py_CompileString = CPyExtFunction(
        lambda args: compile(
            args[0],
            args[1],
            {
                256: "single",
                257: "exec",
                258: "eval"
            }[args[2]]
        ),
        lambda: (
            ("1 + 2", "foo.py", 256),
            ("1 + 2", "foo.py", 257),
            ("1 + 2", "foo.py", 258),
            ("x = 2", "foo.py", 258),
        ),
        resultspec="O",
        argspec='ssi',
        arguments=["char* source", "char* filename", "int type"],
        cmpfunc=unhandled_error_compare
    )

    test_Py_CompileStringExFlags = CPyExtFunction(
        lambda args: compile(
            args[0],
            args[1],
            {
                256: "single",
                257: "exec",
                258: "eval"
            }[args[2]],
            flags=args[3],
            _feature_version=args[4],
            optimize=args[5],
        ),
        lambda: (
            ("1 + 2", "foo.py", 256, 0, 0, -1),
            ("1 + 2", "foo.py", 257, 0, 0, 1),
            ("1 + 2", "foo.py", 258, 0, 0, 2),
            ("x = 2", "foo.py", 258, 0, 0, 0),
        ),
        code="""
            PyObject* wrap_Py_CompileStringExFlags(const char *str, const char *filename_str, int start, int cf_flags,
                    int cf_feature_version, int optimize) {
                PyCompilerFlags flags = {cf_flags, cf_feature_version};
                return Py_CompileStringExFlags(str, filename_str, start, &flags, optimize);
            }
        """,
        callfunction='wrap_Py_CompileStringExFlags',
        resultspec="O",
        argspec='ssiiii',
        arguments=["char* source", "char* filename", "int type", "int cf_flags", "int cf_feature_version",
                   "int optimize"],
        cmpfunc=unhandled_error_compare
    )

    test_Py_CompileStringObject = CPyExtFunction(
        lambda args: compile(
            args[0],
            args[1],
            {
                256: "single",
                257: "exec",
                258: "eval"
            }[args[2]],
            flags=args[3],
            _feature_version=args[4],
            optimize=args[5],
        ),
        lambda: (
            ("1 + 2", "foo.py", 256, 0, 0, -1),
            ("1 + 2", "foo.py", 257, 0, 0, 1),
            ("1 + 2", "foo.py", 258, 0, 0, 2),
            ("x = 2", "foo.py", 258, 0, 0, 0),
        ),
        code="""
            PyObject* wrap_Py_CompileStringObject(const char *str, PyObject *filename, int start, int cf_flags,
                    int cf_feature_version, int optimize) {
                PyCompilerFlags flags = {cf_flags, cf_feature_version};
                return Py_CompileStringObject(str, filename, start, &flags, optimize);
            }
        """,
        callfunction='wrap_Py_CompileStringObject',
        resultspec="O",
        argspec='sOiiii',
        arguments=["char* source", "PyObject* filename", "int type", "int cf_flags", "int cf_feature_version",
                   "int optimize"],
        cmpfunc=unhandled_error_compare
    )
