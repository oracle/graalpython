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
import unittest

IS_GRAALPY = sys.implementation.name == "graalpy"


class CmdLineTest(unittest.TestCase):

    CPU_COUNT_CODE = (
        "import os, sys; "
        "print(sys._get_cpu_count_config(), os.cpu_count(), os.process_cpu_count())"
    )

    def run_cpu_count(self, *args, cpu_count_env=None):
        env = os.environ.copy()
        env.pop('PYTHON_CPU_COUNT', None)
        if cpu_count_env is not None:
            env['PYTHON_CPU_COUNT'] = cpu_count_env
        return subprocess.run(
            [sys.executable, *args, '-c', self.CPU_COUNT_CODE],
            capture_output=True,
            text=True,
            env=env,
        )

    def test_cpu_count_overrides(self):
        result = self.run_cpu_count('-X', 'cpu_count=4321')
        self.assertEqual(0, result.returncode, result)
        self.assertEqual('4321 4321 4321', result.stdout.strip())

        result = self.run_cpu_count(cpu_count_env='1234')
        self.assertEqual(0, result.returncode, result)
        self.assertEqual('1234 1234 1234', result.stdout.strip())

        result = self.run_cpu_count('-X', 'cpu_count=5678', cpu_count_env='1234')
        self.assertEqual(0, result.returncode, result)
        self.assertEqual('5678 5678 5678', result.stdout.strip())

    def test_cpu_count_default_and_ignore_environment(self):
        for args in (('-X', 'cpu_count=default'), ('-E',)):
            with self.subTest(args=args):
                result = self.run_cpu_count(*args, cpu_count_env='1234')
                self.assertEqual(0, result.returncode, result)
                config, cpu_count, process_cpu_count = map(int, result.stdout.split())
                self.assertEqual(-1, config)
                self.assertGreater(cpu_count, 0)
                self.assertEqual(cpu_count, process_cpu_count)

    def test_cpu_count_invalid(self):
        for value in ('cpu_count', 'cpu_count=', 'cpu_count=0', 'cpu_count=-1', 'cpu_count=invalid'):
            with self.subTest(xoption=value):
                result = self.run_cpu_count('-X', value)
                self.assertNotEqual(0, result.returncode)

        for value in ('0', '-1', 'invalid', 'cpu_count=1'):
            with self.subTest(environment=value):
                result = self.run_cpu_count(cpu_count_env=value)
                self.assertNotEqual(0, result.returncode)

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
