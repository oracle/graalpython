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
import unittest
from pathlib import Path
from unittest import skipIf

from . import GRAALPYTHON


@skipIf(not GRAALPYTHON, "requires GraalPy native memory accounting")
class TestPyMemoryViewFromObjectErrorPath(unittest.TestCase):
    def assert_script_succeeds(self, script):
        # Run each check in a fresh GraalPy process. The RSS assertion is otherwise too noisy because
        # the surrounding graalpytest JVM can grow for reasons unrelated to this native Py_buffer path.
        script = textwrap.dedent(f"""
            import sys

            sys.path.insert(0, {str(Path(__file__).parents[2])!r})
        """) + textwrap.dedent(script)
        env = os.environ.copy()
        env["PYTHONHASHSEED"] = "0"
        process = subprocess.run(
            # CPyExtType imports require debugging builtins in this nested GraalPy process as well.
            [sys.executable, "--experimental-options", "--python.EnableDebuggingBuiltins", "-c", script],
            capture_output=True,
            env=env,
            text=True,
        )
        self.assertEqual(process.returncode, 0, process.stdout + process.stderr)

    def test_construction_failure_releases_acquired_buffer(self):
        self.assert_script_succeeds(r"""
            from tests.cpyext import CPyExtType

            ReleaseOnFailureType = CPyExtType(
                "TestMemoryViewReleaseOnConstructionFailure",
                r'''
                static int release_count = 0;
                static char buf[] = {42};

                static int getbuffer(TestMemoryViewReleaseOnConstructionFailureObject *self, Py_buffer *view, int flags) {
                    // Use a length that is valid for Py_buffer but too large for GraalPy's memoryview shape.
                    // This makes PyObject_GetBuffer succeed and memoryview construction fail afterwards.
                    return PyBuffer_FillInfo(view, (PyObject*)self, buf, PY_SSIZE_T_MAX, 1, flags);
                }

                static void releasebuffer(TestMemoryViewReleaseOnConstructionFailureObject *self, Py_buffer *view) {
                    release_count++;
                }

                static PyObject* get_release_count(PyObject* self, PyObject* args) {
                    return PyLong_FromLong(release_count);
                }

                static PyBufferProcs as_buffer = {
                    (getbufferproc)getbuffer,
                    (releasebufferproc)releasebuffer,
                };
                ''',
                tp_as_buffer='&as_buffer',
                tp_methods='{"get_release_count", get_release_count, METH_NOARGS, ""}',
            )

            obj = ReleaseOnFailureType()
            try:
                memoryview(obj)
            except Exception:
                pass
            else:
                raise AssertionError("memoryview unexpectedly succeeded")

            release_count = obj.get_release_count()
            if release_count != 1:
                raise AssertionError(f"expected one releasebuffer call, got {release_count}")
        """)
