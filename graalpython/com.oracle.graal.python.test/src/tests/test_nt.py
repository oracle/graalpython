# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
import errno
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest


JAVA_BACKEND = sys.implementation.name == 'graalpy' and __graalpython__.posix_module_backend() == 'java'


@unittest.skipUnless(sys.platform == 'win32', 'requires Windows')
class NtTests(unittest.TestCase):

    @unittest.skipIf(JAVA_BACKEND, 'requires the Windows native POSIX backend')
    def test_app_execution_alias_stat(self):
        local_app_data = os.environ.get('LOCALAPPDATA')
        if not local_app_data:
            self.skipTest('requires a WindowsApps directory')
        root = Path(local_app_data) / 'Microsoft' / 'WindowsApps'
        aliases = list(root.glob('*.exe'))
        if not aliases:
            self.skipTest('requires an app execution alias')
        for alias in aliases:
            with self.subTest(alias=alias):
                result = os.stat(alias)
                self.assertEqual(result, os.lstat(alias))
                self.assertTrue(stat.S_ISREG(result.st_mode))
                self.assertTrue(alias.exists())
                self.assertTrue(alias.is_file())

    def _create_symlink(self, target, link):
        import ctypes
        from ctypes import wintypes

        kernel32 = ctypes.WinDLL('kernel32', use_last_error=True)
        create_link = kernel32.CreateSymbolicLinkW
        create_link.argtypes = (wintypes.LPCWSTR, wintypes.LPCWSTR, wintypes.DWORD)
        create_link.restype = ctypes.c_ubyte
        if not create_link(link, target, 2):  # SYMBOLIC_LINK_FLAG_ALLOW_UNPRIVILEGED_CREATE
            error = ctypes.get_last_error()
            if error == 1314:  # ERROR_PRIVILEGE_NOT_HELD
                self.skipTest('symbolic link creation requires a Windows privilege')
            raise ctypes.WinError(error)
        get_attributes = kernel32.GetFileAttributesW
        get_attributes.argtypes = (wintypes.LPCWSTR,)
        get_attributes.restype = wintypes.DWORD
        return get_attributes

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not implement Windows permissions')
    def test_chmod_path(self):
        fd, path = tempfile.mkstemp()
        os.close(fd)
        try:
            original = os.stat(path)
            for follow_symlinks in (False, True):
                os.chmod(path, 0o444, follow_symlinks=follow_symlinks)
                self.assertFalse(os.stat(path).st_mode & stat.S_IWRITE)
                os.chmod(path, 0o666, follow_symlinks=follow_symlinks)
                self.assertTrue(os.stat(path).st_mode & stat.S_IWRITE)
                self.assertEqual(original.st_mtime_ns, os.stat(path).st_mtime_ns)
        finally:
            os.chmod(path, 0o666)
            os.unlink(path)

        for follow_symlinks in (False, True):
            with self.assertRaises(FileNotFoundError) as error:
                os.chmod(path, 0o666, follow_symlinks=follow_symlinks)
            self.assertEqual(2, error.exception.winerror)  # ERROR_FILE_NOT_FOUND

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not implement Windows permissions')
    def test_chmod_missing_network_share(self):
        with tempfile.TemporaryDirectory() as directory:
            path = '\\\\localhost\\graalpy-missing-' + os.path.basename(directory) + '\\file'
            for follow_symlinks in (False, True):
                with self.subTest(follow_symlinks=follow_symlinks):
                    with self.assertRaises(FileNotFoundError) as error:
                        os.chmod(path, 0o666, follow_symlinks=follow_symlinks)
                    self.assertEqual(errno.ENOENT, error.exception.errno)
                    self.assertIn(error.exception.winerror, (53, 67))  # BAD_NETPATH / BAD_NET_NAME

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not implement Windows permissions')
    def test_chmod_directory(self):
        with tempfile.TemporaryDirectory() as path:
            try:
                for follow_symlinks in (False, True):
                    os.chmod(path, 0o444, follow_symlinks=follow_symlinks)
                    self.assertFalse(os.stat(path).st_mode & stat.S_IWRITE)
                    os.chmod(path, 0o666, follow_symlinks=follow_symlinks)
                    self.assertTrue(os.stat(path).st_mode & stat.S_IWRITE)
            finally:
                os.chmod(path, 0o666)

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not implement Windows permissions')
    def test_chmod_symlink(self):
        with tempfile.TemporaryDirectory() as root:
            target = os.path.join(root, 'target')
            link = os.path.join(root, 'link')
            with open(target, 'wb') as f:
                f.write(b'content')
            get_attributes = self._create_symlink(target, link)
            try:
                for follow_symlinks in (None, False, True):
                    os.chmod(target, 0o666)
                    os.chmod(link, 0o666, follow_symlinks=False)
                    if follow_symlinks is None:
                        os.chmod(link, 0o444)
                    else:
                        os.chmod(link, 0o444, follow_symlinks=follow_symlinks)
                    self.assertEqual(follow_symlinks is True,
                                     bool(get_attributes(target) & stat.FILE_ATTRIBUTE_READONLY))
                    self.assertEqual(follow_symlinks is not True,
                                     bool(get_attributes(link) & stat.FILE_ATTRIBUTE_READONLY))
                    self.assertTrue(get_attributes(link) & stat.FILE_ATTRIBUTE_REPARSE_POINT)
            finally:
                os.chmod(link, 0o666, follow_symlinks=False)
                os.chmod(target, 0o666)

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not implement Windows permissions')
    def test_chmod_broken_symlink(self):
        with tempfile.TemporaryDirectory() as root:
            target = os.path.join(root, 'missing')
            link = os.path.join(root, 'link')
            get_attributes = self._create_symlink(target, link)
            try:
                os.chmod(link, 0o444)
                self.assertTrue(get_attributes(link) & stat.FILE_ATTRIBUTE_READONLY)
                with self.assertRaises(FileNotFoundError):
                    os.chmod(link, 0o666, follow_symlinks=True)
            finally:
                os.chmod(link, 0o666, follow_symlinks=False)

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not implement Windows permissions')
    def test_chmod_open_file(self):
        fd, path = tempfile.mkstemp()
        try:
            for follow_symlinks in (False, True):
                os.chmod(path, 0o444, follow_symlinks=follow_symlinks)
                self.assertFalse(os.stat(path).st_mode & stat.S_IWRITE)
                os.chmod(path, 0o7777, follow_symlinks=follow_symlinks)
                self.assertTrue(os.stat(path).st_mode & stat.S_IWRITE)
            os.write(fd, b'content')
            os.lseek(fd, 0, os.SEEK_SET)
            self.assertEqual(b'content', os.read(fd, 7))
        finally:
            os.close(fd)
            os.unlink(path)

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not implement Windows permissions')
    def test_fchmod(self):
        self.assertIn(os.chmod, os.supports_fd)
        fd, path = tempfile.mkstemp()
        try:
            os.write(fd, b'content')
            original = os.fstat(fd)
            for chmod in (os.chmod, os.fchmod):
                chmod(fd, 0o444)
                self.assertFalse(os.stat(path).st_mode & stat.S_IWRITE)
                chmod(fd, 0o666)
                self.assertTrue(os.stat(path).st_mode & stat.S_IWRITE)
                self.assertEqual(original.st_mtime_ns, os.fstat(fd).st_mtime_ns)
                self.assertEqual(original.st_size, os.fstat(fd).st_size)
        finally:
            os.chmod(path, 0o666)
            os.close(fd)
            os.unlink(path)

        for chmod in (os.chmod, os.fchmod):
            with self.assertRaises(OSError) as error:
                chmod(fd, 0o666)
            self.assertEqual(errno.EBADF, error.exception.errno)

        with self.assertRaises(OSError) as error:
            os.fchmod(-1, 0o666)
        self.assertEqual(errno.EBADF, error.exception.errno)
