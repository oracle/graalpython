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
import sysconfig
import unittest


def skip_unless_graalos():
    soabi = sysconfig.get_config_var("SOABI") or ""
    if "graalos" not in soabi:
        raise unittest.SkipTest(f"requires GraalOS SOABI, got {soabi!r}")


class GraalOSStandaloneTests(unittest.TestCase):

    def setUp(self):
        skip_unless_graalos()

    def test_sqlite3_native_extension_smoke(self):
        import _sqlite3
        import sqlite3

        self.assertTrue(_sqlite3.sqlite_version)
        conn = sqlite3.connect(":memory:")
        try:
            conn.execute("create table values_for_sum(value integer)")
            conn.executemany("insert into values_for_sum(value) values (?)", [(1,), (2,), (3,)])
            self.assertEqual(conn.execute("select sum(value) from values_for_sum").fetchone()[0], 6)
        finally:
            conn.close()

    def test_demo_packages(self):
        import asteval
        import rich

        self.assertTrue(asteval.__version__)
        self.assertTrue(rich.get_console())

    def test_sandbox_chat_demo(self):
        result = subprocess.run(
            [sys.executable, "/graalos_sandbox_chat.py", "--demo"],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("sum([i*i for i in range(1000)])", result.stdout)
        self.assertIn("__import__('socket').create_connection", result.stdout)
        self.assertIn("gaierror", result.stdout)
        self.assertIn("FileNotFoundError", result.stdout)
        self.assertIn("operation denied", result.stdout)
