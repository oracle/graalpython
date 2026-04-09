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
import tempfile
import unittest


@unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific test")
class EntropySubprocessTests(unittest.TestCase):
    HASH_SECRET_BYTES = 24
    RANDOM_SEED_BYTES = 624 * 4
    HASH_AND_RANDOM_IMPORT_BYTES = HASH_SECRET_BYTES + RANDOM_SEED_BYTES

    def _run_with_init_device(self, data: bytes, code: str):
        with tempfile.NamedTemporaryFile(delete=False) as f:
            f.write(data)
            path = f.name
        try:
            return self._run_with_init_source(f"device:{path}", code)
        finally:
            os.unlink(path)

    def _run_with_init_source(self, source: str, code: str):
        env = os.environ.copy()
        env.pop("PYTHONHASHSEED", None)
        return subprocess.run(
            [
                sys.executable,
                "-S",
                "--experimental-options=true",
                f"--python.InitializationEntropySource={source}",
                "-c",
                code,
            ],
            capture_output=True,
            text=True,
            env=env,
        )

    def assert_initrandom_exhausted(self, result):
        self.assertNotEqual(0, result.returncode, result)
        combined = f"{result.stdout}\n{result.stderr}"
        self.assertIn("initialization entropy device exhausted", combined)

    def assert_subprocess_ok(self, result):
        self.assertEqual(0, result.returncode, result)

    def test_startup_hash_secret_uses_initrandom(self):
        result = self._run_with_init_device(b"\x00" * 4, "print('ok')")
        self.assert_initrandom_exhausted(result)

    def test__random_random_uses_initrandom(self):
        result = self._run_with_init_device(
            b"\x00" * self.HASH_SECRET_BYTES,
            "import _random; _random.Random(); print('ok')",
        )
        self.assert_initrandom_exhausted(result)

    def test_random_module_import_uses_initrandom(self):
        result = self._run_with_init_device(
            b"\x00" * self.HASH_SECRET_BYTES,
            "import random; print('ok')",
        )
        self.assert_initrandom_exhausted(result)

    def test_systemrandom_does_not_mutate_random_state(self):
        result = self._run_with_init_source(
            "fixed:0x1234ABCD",
            "import random; before = random.getstate(); "
            "random.SystemRandom().getrandbits(32); "
            "after = random.getstate(); "
            "print(before == after)",
        )
        self.assert_subprocess_ok(result)
        self.assertEqual("True", result.stdout.strip())

    def test_secrets_does_not_mutate_random_state(self):
        result = self._run_with_init_source(
            "fixed:0x1234ABCD",
            "import random; before = random.getstate(); "
            "import secrets; secrets.token_hex(8); "
            "after = random.getstate(); "
            "print(before == after)",
        )
        self.assert_subprocess_ok(result)
        self.assertEqual("True", result.stdout.strip())

    def test_os_urandom_does_not_use_initrandom(self):
        result = self._run_with_init_device(
            b"\x00" * self.HASH_SECRET_BYTES,
            "import os; print(len(os.urandom(16)))",
        )
        self.assert_subprocess_ok(result)
        self.assertEqual("16", result.stdout.strip())

    def test_multiprocessing_process_import_does_not_use_initrandom(self):
        result = self._run_with_init_device(
            b"\x00" * self.HASH_SECRET_BYTES,
            "import multiprocessing.process; print('ok')",
        )
        self.assert_subprocess_ok(result)
        self.assertEqual("ok", result.stdout.strip())

    def test_pyexpat_import_does_not_use_additional_initrandom(self):
        result = self._run_with_init_device(
            b"\x00" * self.HASH_SECRET_BYTES,
            "import pyexpat; print(pyexpat.__name__)",
        )
        self.assert_subprocess_ok(result)
        self.assertEqual("pyexpat", result.stdout.strip())

    def test_sqlite3_import_does_not_use_additional_initrandom(self):
        result = self._run_with_init_device(
            b"\x00" * self.HASH_SECRET_BYTES,
            "import _sqlite3; print(_sqlite3.__name__)",
        )
        self.assert_subprocess_ok(result)
        self.assertEqual("_sqlite3", result.stdout.strip())

    def test_uuid4_does_not_use_initrandom(self):
        result = self._run_with_init_device(
            b"\x00" * self.HASH_SECRET_BYTES,
            "import uuid; print(uuid.uuid4().version)",
        )
        self.assert_subprocess_ok(result)
        self.assertEqual("4", result.stdout.strip())
