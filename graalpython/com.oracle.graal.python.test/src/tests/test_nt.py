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


@unittest.skipUnless(sys.platform == 'win32', 'requires Windows')
class NtSymlinkTests(unittest.TestCase):
    def create_link(self, source, destination, **kwargs):
        try:
            os.symlink(source, destination, **kwargs)
        except OSError as exc:
            if getattr(exc, 'winerror', None) == 1314:
                self.skipTest('symlink creation requires Windows privilege or Developer Mode')
            raise

    @unittest.skipIf(JAVA_BACKEND, 'Java backend resolves symlink targets instead of reading link text')
    def test_readlink_long_target(self):
        targets = ['x' * length for length in (259, 260, 261)]
        targets += ['sub/' * 90 + 'missing', 'sub/' * 750 + 'missing-\U0001f40d']
        with tempfile.TemporaryDirectory() as directory:
            link = os.path.join(directory, 'link')
            for target in targets:
                with self.subTest(length=len(target)):
                    self.create_link(target, link)
                    try:
                        self.assertEqual(target, os.readlink(link))
                        self.assertEqual(os.fsencode(target), os.readlink(os.fsencode(link)))
                    finally:
                        os.unlink(link)

    @unittest.skipIf(JAVA_BACKEND, 'Java backend resolves symlink targets instead of reading link text')
    def test_dangling_relative_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            lock = Path(directory) / '.lock'
            self.create_link(str(os.getpid()), lock)
            self.assertTrue(lock.is_symlink())
            self.assertFalse(lock.exists())
            self.assertEqual(os.readlink(lock), str(os.getpid()))
            with self.assertRaises(FileNotFoundError):
                lock.stat()
            lock.unlink()

    @unittest.skipIf(JAVA_BACKEND, 'Java backend resolves symlink targets instead of reading link text')
    def test_unicode_file_target(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / 'target_\u03be'
            target.write_text('contents')
            link = root / 'link_\u03be'
            self.create_link(target.name, link)
            self.assertTrue(link.is_symlink())
            self.assertTrue(link.is_file())
            self.assertEqual(os.readlink(link), target.name)
            self.assertEqual(link.read_text(), 'contents')
            link.unlink()

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not support Windows directory symlink semantics')
    def test_directory_target(self):
        for explicit_directory in (False, True):
            with self.subTest(explicit_directory=explicit_directory):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    (root / 'target').mkdir()
                    link = root / 'link'
                    self.create_link('target', link, target_is_directory=explicit_directory)
                    self.assertTrue(link.is_symlink())
                    self.assertTrue(link.is_dir())
                    os.rmdir(link)

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not support Windows directory symlink semantics')
    def test_directory_target_at_long_path_threshold(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            padding = 250 - len(str(root / 'parent' / 'link')) - 1
            if padding < 1:
                self.skipTest('temporary directory path is too long for the threshold test')
            root /= 'd' * padding
            target = root / 'target'
            target.mkdir(parents=True)
            (target / 'contents').write_text('preserved')
            parent = root / 'parent'
            parent.mkdir()
            link = parent / 'link'
            self.assertEqual(len(str(link)), 250)
            self.create_link('..\\target', link)
            try:
                self.assertTrue(link.is_dir())
                self.assertEqual((link / 'contents').read_text(), 'preserved')
            finally:
                link.unlink()

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not support Windows directory symlink semantics')
    def test_unlink_directory_symlink(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / 'target'
            target.mkdir()
            (target / 'contents').write_text('preserved')
            for dangling in (False, True):
                for unlink in (os.unlink, Path.unlink):
                    with self.subTest(dangling=dangling, unlink=unlink):
                        link = root / 'link'
                        self.create_link('missing' if dangling else 'target', link, target_is_directory=True)
                        unlink(link)
                        self.assertFalse(link.is_symlink())
                        self.assertEqual((target / 'contents').read_text(), 'preserved')

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not implement Windows permissions')
    def test_lstat_symlink_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'target'
            target.write_bytes(b'0123456789')
            link = Path(directory) / 'link'
            self.create_link(target.name, link)
            try:
                for mode in (0o444, 0o666):
                    os.chmod(link, mode, follow_symlinks=False)
                    for result in (os.lstat(link), os.stat(link, follow_symlinks=False)):
                        self.assertTrue(stat.S_ISLNK(result.st_mode))
                        self.assertEqual(result.st_size, 0)
                        self.assertEqual(bool(result.st_mode & stat.S_IWRITE), bool(mode & stat.S_IWRITE))
                    self.assertEqual(os.stat(link).st_size, 10)
                    self.assertTrue(os.stat(link).st_mode & stat.S_IWRITE)
                    self.assertNotEqual(os.lstat(link).st_ino, os.stat(target).st_ino)
            finally:
                os.chmod(link, 0o666, follow_symlinks=False)

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not support Windows directory symlink semantics')
    def test_lstat_symlink_executable_extensions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / 'target'
            target.write_text('contents')
            for extension in ('.exe', '.BAT', '.cmd', '.CoM', '.txt', ''):
                with self.subTest(extension=extension):
                    link = root / ('link' + extension)
                    self.create_link(target.name, link)
                    try:
                        for result in (os.lstat(link), os.stat(link, follow_symlinks=False)):
                            self.assertTrue(stat.S_ISLNK(result.st_mode))
                            self.assertEqual(result.st_mode & 0o111,
                                             0o111 if extension.lower() in ('.exe', '.bat', '.cmd', '.com') else 0)
                    finally:
                        link.unlink()

    @unittest.skipIf(JAVA_BACKEND, 'Java backend does not support Windows directory symlink semantics')
    def test_dangling_directory_target(self):
        with tempfile.TemporaryDirectory() as directory:
            link = Path(directory) / 'link'
            self.create_link('missing', link, target_is_directory=True)
            self.assertTrue(link.is_symlink())
            self.assertFalse(link.exists())
            os.rmdir(link)

    def test_existing_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            link = Path(directory) / 'link'
            self.create_link('missing', link)
            with self.assertRaises(FileExistsError):
                os.symlink('other', link)
            link.unlink()

    def test_readlink_regular_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'file'
            path.write_text('contents')
            with self.assertRaises(OSError) as caught:
                os.readlink(path)
            self.assertEqual(caught.exception.errno, errno.EINVAL)
