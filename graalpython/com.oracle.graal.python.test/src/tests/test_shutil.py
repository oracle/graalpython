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

import unittest
import shutil
import io
import os
import pathlib
import re


class TestShUtil(unittest.TestCase):

    def test_disk_usage_returns_a_tuple(self):
        usage = shutil.disk_usage(__file__)
        self.assertIsInstance(usage, tuple)
        self.assertGreater(usage.total, 0)
        self.assertGreater(usage.used, 0)
        self.assertGreater(usage.free, 0)
        self.assertGreater(usage.total, usage.used)
        self.assertGreater(usage.total, usage.free)

    def test_disk_usage_accepts_path_as_a_string(self):
        usage = shutil.disk_usage(__file__)
        self.assertIsInstance(usage, tuple)

    def test_disk_usage_accepts_path_as_bytes(self):
        usage = shutil.disk_usage(__file__.encode("utf-8"))
        self.assertIsInstance(usage, tuple)

    def test_disk_usage_accepts_path_as_a_pathlike(self):
        usage = shutil.disk_usage(pathlib.PurePath(__file__))
        self.assertIsInstance(usage, tuple)

    def test_disk_usage_accepts_file_descriptor(self):
        with io.open(__file__) as file:
            fd = file.fileno()
            usage = shutil.disk_usage(fd)
            self.assertIsInstance(usage, tuple)

    def test_disk_usage_supports_path_to_file(self):
        usage = shutil.disk_usage(__file__)
        self.assertIsInstance(usage, tuple)

    def test_disk_usage_supports_path_to_directory(self):
        usage = shutil.disk_usage(os.path.dirname(__file__))
        self.assertIsInstance(usage, tuple)

    def test_disk_usage_raises_exception_when_given_path_type_is_not_supported(self):
        with self.assertRaisesRegex(TypeError, r'path should be string, bytes, os.PathLike or integer, not list'):
            shutil.disk_usage([])

    def test_disk_usage_raises_exception_when_given_path_does_not_exist(self):
        not_existing_path = __file__ + '.not-existing'
        not_existing_file_name = os.path.basename(not_existing_path)

        with self.assertRaisesRegex(FileNotFoundError, rf"No such file or directory: '.+{not_existing_file_name}'"):
            shutil.disk_usage(not_existing_path)

    def test_disk_usage_raises_exception_when_there_is_no_open_file_with_given_file_descriptor(self):
        not_existing_file_descriptor = 1_000_000 # expect the current process to not open 1M files

        with self.assertRaisesRegex(OSError, r'(B|b)ad file descriptor: 1000000'):
            shutil.disk_usage(not_existing_file_descriptor)