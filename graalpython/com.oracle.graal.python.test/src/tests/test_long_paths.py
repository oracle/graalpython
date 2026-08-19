# Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
import shutil
import stat
import tempfile
import unittest


class LongPathTests(unittest.TestCase):
    def test_long_paths(self):
        root = tempfile.mkdtemp()
        old_cwd = os.getcwd()
        directories = []
        try:
            long_dir = root
            component = 'long-path-component-' + 'x' * 80
            while len(long_dir) < 300:
                long_dir = os.path.join(long_dir, component)
                os.mkdir(long_dir)
                directories.append(long_dir)
            self.assertGreater(len(long_dir), 260)
            self.assertTrue(os.path.isdir(long_dir))

            long_file = os.path.join(long_dir, 'long-path-file.txt')
            fd = os.open(long_file, os.O_CREAT | os.O_WRONLY)
            try:
                os.write(fd, b'content')
            finally:
                os.close(fd)
            self.assertEqual(7, os.stat(long_file).st_size)
            self.assertTrue(os.access(long_file, os.R_OK | os.W_OK))
            os.chmod(long_file, stat.S_IREAD | stat.S_IWRITE)
            os.utime(long_file, (1, 2))
            os.truncate(long_file, 3)
            self.assertEqual(3, os.stat(long_file).st_size)

            renamed = os.path.join(long_dir, 'renamed-long-path-file.txt')
            os.rename(long_file, renamed)
            replacement = os.path.join(long_dir, 'replacement-long-path-file.txt')
            fd = os.open(replacement, os.O_CREAT | os.O_WRONLY)
            os.close(fd)
            os.replace(replacement, renamed)
            self.assertEqual(0, os.stat(renamed).st_size)
            self.assertIn(os.path.basename(renamed), os.listdir(long_dir))
            with os.scandir(long_dir) as entries:
                self.assertIn(os.path.basename(renamed), [entry.name for entry in entries])

            # Short relative paths also need conversion when the current directory is long.
            os.chdir(long_dir)
            self.assertEqual(long_dir, os.getcwd())
            relative_to_long_cwd = 'relative-file.txt'
            fd = os.open(relative_to_long_cwd, os.O_CREAT | os.O_WRONLY)
            os.close(fd)
            os.unlink(relative_to_long_cwd)

            # Exercise a relative path whose text itself exceeds MAX_PATH.
            os.chdir(root)
            relative_long_file = os.path.relpath(renamed, root)
            self.assertGreater(len(relative_long_file), 260)
            self.assertEqual(0, os.stat(relative_long_file).st_size)
            os.unlink(relative_long_file)

            os.chdir(old_cwd)
            for directory in reversed(directories):
                os.rmdir(directory)
            os.rmdir(root)
            root = None
        finally:
            os.chdir(old_cwd)
            if root is not None:
                shutil.rmtree(root)
