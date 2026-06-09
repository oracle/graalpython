# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import os
import subprocess
import sys
import textwrap

from . import GRAALPYTHON, compile_module_from_string

if GRAALPYTHON:
    import __graalpython__


def test_private_log_handles_long_formatted_messages():
    if not GRAALPYTHON or not sys.executable:
        return

    module = compile_module_from_string("""
        #define PY_SSIZE_T_CLEAN
        #include <Python.h>

        #ifdef GRAALVM_PYTHON
        #include <graalpy/testcapi.h>

        #define GRAALPY_LOG_INFO 0x2

        static void call_private_log_impl(const char *format, ...) {
            va_list args;
            va_start(args, format);
            GraalPyTestCAPI->GraalPyPrivate_LogImpl(GRAALPY_LOG_INFO, format, args);
            va_end(args);
        }
        #endif

        #define X10 "aaaaaaaaaa"
        #define X100 X10 X10 X10 X10 X10 X10 X10 X10 X10 X10
        #define X1000 X100 X100 X100 X100 X100 X100 X100 X100 X100 X100
        #define LONG_LOG_MESSAGE X1000 X1000

        static PyObject* trigger_long_log_message(PyObject* module, PyObject* unused) {
        #ifdef GRAALVM_PYTHON
            if (GraalPyTestCAPI_Import() < 0) {
                return NULL;
            }
            call_private_log_impl("%s", LONG_LOG_MESSAGE);
        #endif
            Py_RETURN_NONE;
        }

        static PyMethodDef module_methods[] = {
            {"trigger_long_log_message", trigger_long_log_message, METH_NOARGS, ""},
            {NULL}
        };

        static PyModuleDef module_def = {
            PyModuleDef_HEAD_INIT, "graalpy_capi_log_overflow", "", -1, module_methods
        };

        PyMODINIT_FUNC PyInit_graalpy_capi_log_overflow(void) {
            return PyModule_Create(&module_def);
        }
    """, "graalpy_capi_log_overflow")

    module_dir = os.path.dirname(module.__file__)
    script = textwrap.dedent(f"""
        import sys
        sys.path.insert(0, {module_dir!r})
        import graalpy_capi_log_overflow
        graalpy_capi_log_overflow.trigger_long_log_message()
        print("done")
    """)
    args = [sys.executable, "--experimental-options=true", "--python.EnableDebuggingBuiltins"]

    args += ["--log.python.capi.PythonCextBuiltins.level=INFO", "-c", script]

    proc = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if proc.returncode != 0:
        stderr_tail = "\n".join(proc.stderr.splitlines()[-40:])
        message = (
            f"process exited with {proc.returncode}\n"
            f"stdout:\n{proc.stdout}\n"
            f"stderr tail:\n{stderr_tail}"
        )
        raise AssertionError(message)
    assert "done" in proc.stdout
