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

import subprocess
import sys
import unittest

IS_GRAALPY = sys.implementation.name == "graalpy"


class CmdLineTest(unittest.TestCase):

    def test_stdin_script_exit_code(self):
        code = "import sys\nsys.exit(42)\n"
        result = subprocess.run([sys.executable], input=code, text=True)
        self.assertEqual(42, result.returncode)

    @unittest.skipUnless(IS_GRAALPY, "GraalPy-specific test")
    def test_jit_mode_presets(self):
        for mode in ('0', '1', '2'):
            result = subprocess.run(
                [sys.executable, '-X', f'jit={mode}', '-c', '1'],
                capture_output=True,
                text=True,
            )
            self.assertEqual(0, result.returncode, result)

    @unittest.skipUnless(IS_GRAALPY, "GraalPy-specific test")
    def test_jit_mode_invalid_value(self):
        result = subprocess.run(
            [sys.executable, '-X', 'jit=3', '-c', 'pass'],
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn('expected jit=0, jit=1, or jit=2', result.stderr)

