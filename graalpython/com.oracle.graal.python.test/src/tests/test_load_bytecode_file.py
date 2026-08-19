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

import _imp
import os
import py_compile
import sys
import tempfile
import types
import unittest
from pathlib import Path

INVALIDATION_MODES = (
    py_compile.PycInvalidationMode.TIMESTAMP,
    py_compile.PycInvalidationMode.CHECKED_HASH,
    py_compile.PycInvalidationMode.UNCHECKED_HASH,
)
CHECK_HASH_MODES = ("default", "always", "never")


@unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific bytecode loader test")
class LoadBytecodeFileTests(unittest.TestCase):
    @staticmethod
    def source_stat(source_path):
        source_stat = source_path.stat()
        return {"mtime": source_stat.st_mtime, "size": source_stat.st_size}

    def test_outdated_timestamp(self):
        with tempfile.TemporaryDirectory() as tempdir:
            source_path = Path(tempdir) / "example.py"
            source_path.write_bytes(b"value = 42\n")
            os.utime(source_path, (50, 50))
            bytecode_path = Path(py_compile.compile(
                source_path,
                doraise=True,
                invalidation_mode=py_compile.PycInvalidationMode.TIMESTAMP,
            ))

            os.utime(source_path, (100, 100))
            code = __graalpython__.load_bytecode_file(
                bytecode_path, source_path, self.source_stat(source_path)
            )
            self.assertIsNone(code)

    def test_too_short(self):
        with tempfile.TemporaryDirectory() as tempdir:
            source_path = Path(tempdir) / "example.py"
            source_path.write_bytes(b"value = 42\n")
            bytecode_path = Path(py_compile.compile(source_path, doraise=True))
            bytecode_path.write_bytes(bytecode_path.read_bytes()[:15])

            code = __graalpython__.load_bytecode_file(
                bytecode_path, source_path, self.source_stat(source_path)
            )
            self.assertIsNone(code)

    def test_invalidation_and_hash_check_modes(self):
        original_check_hash_based_pycs = _imp.check_hash_based_pycs
        self.addCleanup(setattr, _imp, "check_hash_based_pycs", original_check_hash_based_pycs)

        for invalidation_mode in INVALIDATION_MODES:
            for check_hash_mode in CHECK_HASH_MODES:
                with self.subTest(invalidation_mode=invalidation_mode, check_hash_mode=check_hash_mode):
                    with tempfile.TemporaryDirectory() as tempdir:
                        source_path = Path(tempdir) / "example.py"
                        source_path.write_bytes(b"value = 'old'\n")
                        timestamp = (50, 50)
                        os.utime(source_path, timestamp)
                        bytecode_path = Path(py_compile.compile(
                            source_path,
                            doraise=True,
                            invalidation_mode=invalidation_mode,
                        ))

                        _imp.check_hash_based_pycs = check_hash_mode
                        code = __graalpython__.load_bytecode_file(
                            bytecode_path, source_path, self.source_stat(source_path)
                        )
                        self.assertIsInstance(code, types.CodeType)
                        ns = {}
                        exec(code, ns)
                        self.assertEqual(ns.get('value'), 'old')

                        # Keep the timestamp and size unchanged so that only hash verification can
                        # distinguish the new source from the source used to create the bytecode.
                        source_path.write_bytes(b"value = 'new'\n")
                        os.utime(source_path, timestamp)
                        code = __graalpython__.load_bytecode_file(
                            bytecode_path, source_path, self.source_stat(source_path)
                        )

                        verifies_hash = (
                                invalidation_mode == py_compile.PycInvalidationMode.CHECKED_HASH
                                and check_hash_mode != "never"
                                or invalidation_mode == py_compile.PycInvalidationMode.UNCHECKED_HASH
                                and check_hash_mode == "always"
                        )
                        if verifies_hash:
                            self.assertIsNone(code)
                        else:
                            self.assertIsInstance(code, types.CodeType)
                            ns = {}
                            exec(code, ns)
                            self.assertEqual(ns.get('value'), 'old')
