# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

from tests.testlib_helper import build_testlib


class TestCtypesInterop(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        orig_root = Path(__file__).parent.resolve()
        orig_testlib = orig_root / "testlib"
        cls.tmpdir = Path(tempfile.mkdtemp(prefix="testctypes_tmp_"))
        cls.lib_path = build_testlib(cls.tmpdir, orig_testlib)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmpdir, ignore_errors=True)

    def run_in_subprocess(self, code, *args):
        proc = subprocess.run(
            [sys.executable, "-c", code, *args],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        if proc.returncode != 0:
            self.fail(
                "Subprocess failed with exit code {}\nstdout:\n{}\nstderr:\n{}".format(
                    proc.returncode, proc.stdout, proc.stderr
                )
            )

    def test_ctypes_load_and_call(self):
        # Pass the library path as an argument
        code = textwrap.dedent(
            """
            import sys
            from ctypes import CDLL, c_int
            lib_path = sys.argv[1]
            lib = CDLL(lib_path)
            get_answer = lib.get_answer
            get_answer.restype = c_int
            result = get_answer()
            assert result == 42, f'expected 42, got {result}'
            """
        )
        self.run_in_subprocess(code, str(self.lib_path))

    @unittest.skipIf(sys.platform != "win32", "Windows-only test")
    def test_os_add_dll_directory_and_unload(self):
        # Pass the library dir as argument
        code = textwrap.dedent(
            """
            import os
            import sys
            from pathlib import Path
            from ctypes import CDLL, c_int
            lib_dir = Path(sys.argv[1])
            dll_dir = lib_dir.parent
            dll_name = lib_dir.name
            # Should fail to load when DLL dir not added
            try:
                CDLL(dll_name)
            except OSError:
                pass
            else:
                raise AssertionError("CDLL(dll_name) should fail outside dll dir context")
            # Should succeed when DLL dir is temporarily added
            with os.add_dll_directory(dll_dir):
                lib = CDLL(dll_name)
                get_answer = lib.get_answer
                get_answer.restype = c_int
                result = get_answer()
                assert result == 42, f'expected 42, got {result}'
            """
        )
        self.run_in_subprocess(code, str(self.lib_path))


if __name__ == "__main__":
    unittest.main()
