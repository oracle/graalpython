# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

try:
    __graalpython__.posix_module_backend()
except:
    class GP:
        def posix_module_backend(self):
            return 'cpython'
    __graalpython__ = GP()


import unittest
import os
import array
import sys
import posix
import stat
import tempfile
import io
from contextlib import contextmanager


PREFIX = 'graalpython_test'
TEMP_DIR = tempfile.gettempdir()

TEST_FILENAME1 = f'{PREFIX}_{os.getpid()}_tmp1'
TEST_FILENAME2 = f'{PREFIX}_{os.getpid()}_tmp2'
TEST_FULL_PATH1 = os.path.join(TEMP_DIR, TEST_FILENAME1)
TEST_FULL_PATH2 = os.path.join(TEMP_DIR, TEST_FILENAME2)


@contextmanager
def auto_close(fd):
    try:
        yield fd
    finally:
        os.close(fd)

@contextmanager
def open(name, flags):
    fd = os.open(name, flags)
    try:
        yield fd
    finally:
        os.close(fd)


class PosixTests(unittest.TestCase):

    def test_platform_constants(self):
        if sys.platform == 'darwin':
            self.assertEqual(8, posix.O_APPEND)
        else:
            self.assertEqual(0x400, posix.O_APPEND)

    def test_uname(self):
        # just like cpython, a simple smoke test
        uname = posix.uname()
        self.assertRaises(TypeError, lambda: posix.uname(1))
        self.assertIsNotNone(uname.sysname)
        self.assertIsNotNone(uname.nodename)
        self.assertIsNotNone(uname.release)
        self.assertIsNotNone(uname.version)
        self.assertIsNotNone(uname.machine)

    def test_execv(self):
        # test creates a shell script, which again creates a file, to ensure script execution
        # Both files are deleted again in the end
        new_file_path, cwd = self.create_file()
        os.system("%s -c \"import os; os.execv('%s', ['%s', 'the_input'])\"" % (sys.executable, new_file_path, new_file_path))
        assert os.path.isfile(cwd + '/test.txt')
        self.delete_file(new_file_path, cwd)

    def test_execl(self):
        # test creates a shell script, which again creates a file, to ensure script execution
        # Both files are deleted again in the end
        new_file_path, cwd = self.create_file()
        os.system("%s -c \"import os; os.execl('%s', *['%s', 'the_input'])\"" % (sys.executable, new_file_path, new_file_path))
        assert os.path.isfile(cwd + '/test.txt')
        self.delete_file(new_file_path, cwd)

    def test_execv_with_env(self):
        new_file_path, cwd = self.create_file()
        with io.open(new_file_path, 'w') as script:
            script.write('#!/bin/sh\n')
            script.write('echo $ENV_VAR > {}/test.txt\n'.format(cwd))
        os.system("%s -c \"import os; os.environ['ENV_VAR']='the_text'; os.execv('%s', ['%s', 'the_input'])\"" % (sys.executable, new_file_path, new_file_path))
        assert os.path.isfile(cwd + '/test.txt')
        with io.open(cwd+'/test.txt', 'r') as result:
            assert 'the_text' in result.readline()
        self.delete_file(new_file_path, cwd)

    def test_path_respecialization(self):
        # regression test for https://github.com/graalvm/graalpython/issues/124
        from pathlib import PurePath
        p = PurePath(".")
        for path in [p, "."]:
            os.scandir(path)

    def create_file(self):
        cwd = os.getcwd()
        new_file_path = os.path.join(cwd , 'myscript.sh')
        with io.open(new_file_path, 'w') as script:
            script.write('#!/bin/sh\n')
            script.write("echo \"something echo with\" $1 > {}/test.txt\n".format(cwd))
            script.write('echo this is an output\n')
        assert os.path.isfile(new_file_path)
        st = os.stat(new_file_path)
        os.chmod(new_file_path, st.st_mode | stat.S_IEXEC)
        return new_file_path, cwd

    def test_empty_stat(self):
        with self.assertRaises(FileNotFoundError):
            os.stat('')

    def delete_file(self, new_file_path, cwd):
        os.remove(new_file_path)
        os.remove(cwd + '/test.txt')

    def test_strerror(self):
        # make sure that strerror works even for non-existent error codes
        self.assertTrue(isinstance(os.strerror(2), str))
        self.assertTrue(isinstance(os.strerror(0), str))
        self.assertTrue(isinstance(os.strerror(1234), str))
        self.assertTrue(isinstance(os.strerror(-42), str))

    @unittest.skipUnless(__graalpython__.posix_module_backend() != 'java',
                         'test requires inheritable support')
    def test_pipe(self):
        fd1, fd2 = os.pipe()
        try:
            self.assertFalse(os.get_inheritable(fd1))
            self.assertFalse(os.get_inheritable(fd2))
        finally:
            os.close(fd1)
            os.close(fd2)

    def test_mkdir_rmdir(self):
        os.mkdir(TEST_FULL_PATH1)
        try:
            self.assertTrue(stat.S_ISDIR(os.stat(TEST_FULL_PATH1).st_mode))
        finally:
            os.rmdir(TEST_FULL_PATH1)

    def test_mkdir_rmdir_dirfd(self):
        with open(TEMP_DIR, 0) as tmp_fd:
            os.mkdir(TEST_FILENAME1, dir_fd=tmp_fd)
            try:
                self.assertTrue(stat.S_ISDIR(os.stat(TEST_FULL_PATH1).st_mode))
            finally:
                os.rmdir(TEST_FILENAME1, dir_fd=tmp_fd)

    def test_umask(self):
        orig = os.umask(0o22)
        self.assertEqual(0o22, os.umask(orig))

    def test_fspath(self):
        class StringSubclass(str):
            pass

        class Wrap:
            def __init__(self, val):
                self.val = val

            def __fspath__(self):
                return self.val

        for x in ['abc', b'abc', StringSubclass('abc')]:
            self.assertEqual(x, os.fspath(x))
            self.assertEqual(x, os.fspath(Wrap(x)))

        for x in [bytearray(b'abc'), 42, 3.14]:
            with self.assertRaisesRegex(TypeError, f"expected str, bytes or os.PathLike object, not {type(x).__name__}"):
                os.fspath(x)
        for x in [bytearray(b'abc'), 42, 3.14]:
            with self.assertRaisesRegex(TypeError, r"expected Wrap.__fspath__\(\) to return str or bytes, not " + type(x).__name__):
                os.fspath(Wrap(x))

    def test_path_convertor(self):
        class C:
            def __fspath__(self):
                return bytearray(b'.')

        os.close(os.open(bytearray(b'.'), 0))
        with self.assertRaisesRegex(TypeError, r"expected C.__fspath__\(\) to return str or bytes, not bytearray"):
            os.open(C(), 0)

    def test_fd_converter(self):
        class MyInt(int):
            def fileno(self): return 0

        class MyObj:
            def fileno(self): return -1

        self.assertRaises(ValueError, os.fsync, -1)
        self.assertRaises(ValueError, os.fsync, MyInt(-1)) # fileno should be ignored
        self.assertRaises(ValueError, os.fsync, MyObj())

    @unittest.skipIf(not hasattr(os, 'waitstatus_to_exitcode') or __graalpython__.posix_module_backend() == 'java' or sys.platform == 'darwin', 'values are specific to linux')
    def test_waitstatus_to_exitcode(self):
        self.assertRaises(TypeError, os.waitstatus_to_exitcode, "0")
        self.assertRaises(ValueError, os.waitstatus_to_exitcode, -1)
        self.assertEqual(0, os.waitstatus_to_exitcode(0x0000))
        self.assertEqual(1, os.waitstatus_to_exitcode(0x0100))
        self.assertEqual(255, os.waitstatus_to_exitcode(0xFF00))
        self.assertEqual(0, os.waitstatus_to_exitcode(0x10000))
        self.assertEqual(-1, os.waitstatus_to_exitcode(0x0001))
        self.assertEqual(-1, os.waitstatus_to_exitcode(0x4201))
        self.assertEqual(-126, os.waitstatus_to_exitcode(0x007E))
        self.assertRaisesRegex(ValueError, r"process stopped by delivery of signal 0", os.waitstatus_to_exitcode, 0x007f)
        self.assertRaisesRegex(ValueError, r"process stopped by delivery of signal 1", os.waitstatus_to_exitcode, 0x017f)
        self.assertRaisesRegex(ValueError, r"process stopped by delivery of signal 255", os.waitstatus_to_exitcode, 0xff7f)
        self.assertRaisesRegex(ValueError, r"process stopped by delivery of signal 0", os.waitstatus_to_exitcode, 0x1007f)
        self.assertEqual(42, os.waitstatus_to_exitcode(0x2A80))
        self.assertEqual(-12, os.waitstatus_to_exitcode(0x428C))
        self.assertRaises(ValueError, os.waitstatus_to_exitcode, 0xFF)

    def test_truncate(self):
        try:
            with open(TEST_FULL_PATH1, os.O_WRONLY | os.O_CREAT) as fd:
                os.write(fd, b'hello world')
            os.truncate(TEST_FULL_PATH1, 5)
            with open(TEST_FULL_PATH1, os.O_RDONLY) as fd:
                self.assertEqual(b'hello', os.read(fd, 100))
        finally:
            try:
                os.unlink(TEST_FULL_PATH1)
            except Exception:
                pass


class WithCurdirFdTests(unittest.TestCase):

    def setUp(self):
        self.fd = os.open('.', 0)

    def tearDown(self):
        os.close(self.fd)

    @unittest.skipUnless(__graalpython__.posix_module_backend() != 'java',
                         'test requires inheritable support')
    def test_inheritable(self):
        self.assertFalse(os.get_inheritable(self.fd))
        os.set_inheritable(self.fd, True)
        self.assertTrue(os.get_inheritable(self.fd))
        with auto_close(os.dup(self.fd)) as fd2:
            self.assertFalse(os.get_inheritable(fd2))
            os.set_inheritable(fd2, True)
            self.assertTrue(os.get_inheritable(fd2))
            os.set_inheritable(fd2, False)
            # dup2 closes fd2 atomically
            os.dup2(self.fd, fd2, True)
            self.assertTrue(os.get_inheritable(fd2))
            os.set_inheritable(fd2, False)
            self.assertFalse(os.get_inheritable(fd2))

    def test_fsync(self):
        os.fsync(self.fd)

    def test_fileno(self):
        fd = self.fd
        class C:
            def fileno(self):
                return fd
        os.fsync(C())

    def test_get_blocking(self):
        self.assertTrue(os.get_blocking(self.fd))

    @unittest.skipUnless(__graalpython__.posix_module_backend() != 'java',
                         'test requires non-blocking access support for regular files')
    def test_change_blocking(self):
        self.assertTrue(os.get_blocking(self.fd))
        os.set_blocking(self.fd, False)
        self.assertFalse(os.get_blocking(self.fd))

    def test_atty(self):
        self.assertFalse(os.isatty(self.fd))


class WithTempFilesTests(unittest.TestCase):

    def setUp(self):
        os.close(os.open(TEST_FULL_PATH1, os.O_WRONLY | os.O_CREAT))
        os.symlink(TEST_FULL_PATH1, TEST_FULL_PATH2)
        self.tmp_fd = os.open(TEMP_DIR, 0)

    def tearDown(self):
        os.close(self.tmp_fd)
        for teardown_file in [TEST_FULL_PATH1, TEST_FULL_PATH2]:
            try:
                os.unlink(teardown_file)
            except (FileNotFoundError, NotADirectoryError):
                pass

    def test_unlink(self):
        os.unlink(TEST_FULL_PATH1)

    def test_unlink_dirfd(self):
        os.unlink(TEST_FILENAME1, dir_fd=self.tmp_fd)

    def test_unlink_remove_err_msg(self):
        with self.assertRaisesRegex(TypeError, 'unlink'):
            os.unlink(3.14)
        with self.assertRaisesRegex(TypeError, 'remove'):
            os.remove(3.14)

    def test_stat(self):
        sr1 = os.stat(TEST_FULL_PATH1)
        self.assertEqual(int(sr1.st_atime), sr1[7])
        self.assertTrue(abs(sr1.st_atime_ns/1000000000 - sr1.st_atime) < 1e-4)
        self.assertEqual(0, sr1.st_size)
        self.assertEqual(sr1.st_ino, os.stat(TEST_FULL_PATH2).st_ino)       # follow_symlinks = True
        sr2 = os.stat(TEST_FULL_PATH2, follow_symlinks=False)
        self.assertNotEqual(sr1.st_ino, sr2.st_ino)

    def test_stat_fd(self):
        inode = os.stat(TEST_FULL_PATH1).st_ino
        with open(TEST_FULL_PATH2, 0) as fd:   # TEST_FULL_PATH2 is a symlink to TEST_FULL_PATH1
            self.assertEqual(inode, os.stat(fd).st_ino)
            with self.assertRaisesRegex(ValueError, "stat: cannot use fd and follow_symlinks together"):
                os.stat(fd, follow_symlinks=False)

    def test_stat_dirfd(self):
        inode = os.stat(TEST_FULL_PATH1).st_ino
        self.assertEqual(inode, os.stat(TEST_FILENAME1, dir_fd=self.tmp_fd).st_ino)
        with self.assertRaisesRegex(ValueError, "stat: can't specify dir_fd without matching path"):
            os.stat(0, dir_fd=self.tmp_fd)
        with self.assertRaisesRegex(ValueError, "stat: can't specify dir_fd without matching path"):
            os.stat(0, dir_fd=self.tmp_fd, follow_symlinks=False)

    def test_lstat(self):
        inode = os.stat(TEST_FULL_PATH2, follow_symlinks=False).st_ino
        self.assertEqual(inode, os.lstat(TEST_FULL_PATH2).st_ino)   # lstat does not follow symlink
        self.assertEqual(inode, os.lstat(TEST_FILENAME2, dir_fd=self.tmp_fd).st_ino)
        with self.assertRaisesRegex(TypeError, "lstat: path should be string, bytes or os.PathLike, not int"):
            os.lstat(self.tmp_fd)

    def test_fstat(self):
        inode = os.stat(TEST_FULL_PATH1).st_ino
        with open(TEST_FULL_PATH2, 0) as fd:           # follows symlink
            self.assertEqual(inode, os.fstat(fd).st_ino)

    @unittest.skipIf(__graalpython__.posix_module_backend() == 'java', 'statvfs emulation is not supported')
    def test_statvfs(self):
        res = os.statvfs(TEST_FULL_PATH1)
        with open(TEST_FULL_PATH1, 0) as fd:
            self.assertEqual(res.f_bsize, os.statvfs(fd).f_bsize)

    def test_utimes(self):
        os.utime(TEST_FULL_PATH2, (-952468575.678901234, 1579569825.123456789))         # follows symlink
        self.assertTrue(os.stat(TEST_FULL_PATH1).st_atime < -900000000)

    def test_utimes(self):
        os.utime(TEST_FULL_PATH2)

    @unittest.skipUnless(__graalpython__.posix_module_backend() != 'java',
                         'Due to bug in OpenJDK 8 on Linux we cannot set atime/mtime of symlinks')
    def test_lutimes(self):
        os.utime(TEST_FULL_PATH2, (-952468575.678901234, 1579569825.123456789))         # follows symlink
        self.assertTrue(os.stat(TEST_FULL_PATH1).st_atime < -900000000)
        os.utime(TEST_FULL_PATH2, ns=(952468575678901234, 1579569825123456789), follow_symlinks=False)
        self.assertTrue(os.stat(TEST_FULL_PATH1).st_atime < -900000000)
        self.assertTrue(abs(os.stat(TEST_FULL_PATH1).st_mtime - 1579569825) < 10)
        self.assertTrue(os.stat(TEST_FULL_PATH2, follow_symlinks=False).st_atime > 900000000)

    def test_utimensat(self):
        if sys.platform == 'darwin':
            with self.assertRaises(NotImplementedError):
                os.utime(TEST_FILENAME2, dir_fd=self.tmp_fd, ns=(952468575678901234, 1579569825123456789))
        else:
            os.utime(TEST_FILENAME2, dir_fd=self.tmp_fd, ns=(952468575678901234, 1579569825123456789))
            self.assertTrue(os.stat(TEST_FULL_PATH2).st_atime > 900000000)

    def test_futimes_and_futimens(self):
        with open(TEST_FULL_PATH2, os.O_RDWR) as fd:
            os.utime(fd, times=(12345, 67890))
            self.assertTrue(abs(os.stat(TEST_FULL_PATH1).st_atime_ns - 12345000000000) < 10000000000)

    @unittest.skipUnless(sys.platform != 'darwin', 'faccessat on MacOSX does not support follow_symlinks')
    def test_access(self):
        self.assertTrue(os.access(TEST_FULL_PATH2, 0))
        self.assertTrue(os.access(TEST_FULL_PATH2, 0, dir_fd=1234567890)) # dir_fd should be ignored
        self.assertTrue(os.access(TEST_FILENAME2, 0, dir_fd=self.tmp_fd))
        self.assertTrue(os.access(TEST_FULL_PATH2, 0, follow_symlinks=False))
        self.assertTrue(os.access(TEST_FILENAME2, 0, dir_fd=self.tmp_fd, follow_symlinks=False))
        self.assertFalse(os.access(TEST_FILENAME2, 0, dir_fd=1234567890)) # non-existing dir_fd -> False
        os.unlink(TEST_FULL_PATH1)
        self.assertFalse(os.access(TEST_FULL_PATH2, 0))
        self.assertFalse(os.access(TEST_FILENAME2, 0, dir_fd=self.tmp_fd))
        self.assertTrue(os.access(TEST_FULL_PATH2, 0, follow_symlinks=False))
        self.assertTrue(os.access(TEST_FILENAME2, 0, dir_fd=self.tmp_fd, follow_symlinks=False))

    def test_chmod(self):
        orig_mode = os.stat(TEST_FULL_PATH1).st_mode & 0o777
        os.chmod(TEST_FILENAME1, 0o624, dir_fd=self.tmp_fd)
        self.assertEqual(0o624, os.stat(TEST_FULL_PATH1).st_mode & 0o777)
        with open(TEST_FULL_PATH1, os.O_RDWR) as fd:
            os.chmod(fd, orig_mode)
        self.assertEqual(orig_mode, os.stat(TEST_FULL_PATH1).st_mode & 0o777)

    @unittest.skipUnless(__graalpython__.posix_module_backend() != 'java' or sys.platform != 'darwin',
                         'TODO: issue with readlink on MacOS')
    def test_readlink(self):
        self.assertEqual(TEST_FULL_PATH1, os.readlink(TEST_FULL_PATH2))
        self.assertEqual(os.fsencode(TEST_FULL_PATH1), os.readlink(os.fsencode(TEST_FULL_PATH2)))
        self.assertEqual(TEST_FULL_PATH1, os.readlink(TEST_FILENAME2, dir_fd=self.tmp_fd))
        self.assertEqual(os.fsencode(TEST_FULL_PATH1), os.readlink(os.fsencode(TEST_FILENAME2), dir_fd=self.tmp_fd))


class ChdirTests(unittest.TestCase):

    def setUp(self):
        self.old_wd = os.getcwd()
        os.mkdir(TEST_FULL_PATH1)

    def tearDown(self):
        os.chdir(self.old_wd)
        os.rmdir(TEST_FULL_PATH1)

    def test_chdir(self):
        os.chdir(TEMP_DIR)
        os.chdir(self.old_wd)
        self.assertEqual(os.fsencode(self.old_wd), os.getcwdb())
        os.chdir(os.fsencode(self.old_wd))
        self.assertEqual(self.old_wd, os.getcwd())

    def test_chdir_relative(self):
        os.chdir(TEMP_DIR)
        tmp_dir = os.getcwd()
        os.chdir(TEST_FILENAME1)
        self.assertEqual(os.path.join(tmp_dir, TEST_FILENAME1), os.getcwd())

    def test_chdir_relative_symlink(self):
        os.symlink(TEST_FULL_PATH1, TEST_FULL_PATH2, target_is_directory=True)
        try:
            os.chdir(TEMP_DIR)
            os.chdir(TEST_FILENAME2)
        finally:
            os.remove(TEST_FULL_PATH2)

    def test_chdir_not_a_dir(self):
        os.close(os.open(TEST_FULL_PATH2, os.O_WRONLY | os.O_CREAT))
        try:
            self.assertRaises(NotADirectoryError, os.chdir, TEST_FULL_PATH2)
        finally:
            os.unlink(TEST_FULL_PATH2)

    def test_chdir_fd(self):
        os.chdir(TEMP_DIR)
        with open(self.old_wd, 0) as fd:
            os.chdir(fd)
            self.assertEqual(self.old_wd, os.getcwd())

    def test_fchdir(self):
        with open(self.old_wd, 0) as fd:
            os.fchdir(fd)
            self.assertEqual(os.fsencode(self.old_wd), os.getcwdb())


class ScandirEmptyTests(unittest.TestCase):

    def setUp(self):
        os.mkdir(TEST_FULL_PATH1)

    def tearDown(self):
        os.rmdir(TEST_FULL_PATH1)

    def test_scandir_empty(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            self.assertEqual(0, len([entry for entry in dir]))

    def test_listdir_empty(self):
        self.assertEqual([], os.listdir(TEST_FULL_PATH1))


class ScandirTests(unittest.TestCase):

    def setUp(self):
        os.mkdir(TEST_FULL_PATH1)
        self.abc_path = os.path.join(TEST_FULL_PATH1, '.abc')
        os.close(os.open(self.abc_path, os.O_WRONLY | os.O_CREAT))

    def tearDown(self):
        try:
            os.unlink(self.abc_path)
        except FileNotFoundError:
            pass
        os.rmdir(TEST_FULL_PATH1)

    def test_scandir_explicit_close(self):
        # __exit__ must deal with explicit close()
        with os.scandir(TEST_FULL_PATH1) as dir:
            dir.close()

        # __exit__ must deal with explicit close(), exhausted dir stream
        with os.scandir(TEST_FULL_PATH1) as dir:
            self.assertEqual(1, len([x for x in dir]))
            dir.close()

        # close() must deal with __exit__
        with os.scandir(TEST_FULL_PATH1) as dir:
            pass
        dir.close()

        # close() must deal with __exit__, exhausted dir stream
        with os.scandir(TEST_FULL_PATH1) as dir:
            self.assertEqual(1, len([x for x in dir]))
        dir.close()

        # __next__ must deal with closed streams
        self.assertRaises(StopIteration, next, dir)
        # second close() is no-op
        dir.close()

    def test_scandir_fd_rewind(self):
        with open(TEST_FULL_PATH1, 0) as fd:
            with os.scandir(fd) as dir:
                self.assertEqual(1, len([x for x in dir]))
            # ScandirIterator.__exit__() must rewind
            # also, fd must still be valid (scandir should do dup)
            dir = os.scandir(fd)
            self.assertEqual(1, len([x for x in dir]))
            dir.close()
            # ScandirIterator.close() must rewind
            self.assertEqual(1, len([x for x in os.scandir(fd)]))
            # ScandirIterator.__next__ must rewind the dir when exhausted even if not closed explicitly
            self.assertEqual(1, len([x for x in os.scandir(fd)]))
            # two dir streams based on the same fd must influence each other
            dir1 = os.scandir(fd)
            dir2 = os.scandir(fd)
            next(dir1)
            dir1.close()
            if sys.platform != 'darwin':
                # ScandirIterator.close() must rewind
                self.assertEqual(1, len([x for x in dir2]))

    def test_scandir_default_arg(self):
        with os.scandir() as dir:
            self.assertEqual('./', next(dir).path[:2])

    def test_scandir_entry_path_str(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        self.assertEqual("<DirEntry '.abc'>", repr(entry))
        self.assertEqual('.abc', entry.name)
        self.assertEqual(self.abc_path, entry.path)
        self.assertEqual(self.abc_path, os.fspath(entry))

        # trailing slash
        with os.scandir(TEST_FULL_PATH1 + '/') as dir:
            self.assertEqual(self.abc_path, next(dir).path)

    def test_scandir_entry_path_bytes(self):
        with os.scandir(os.fsencode(TEST_FULL_PATH1)) as dir:
            entry = next(dir)
        self.assertEqual("<DirEntry b'.abc'>", repr(entry))
        self.assertEqual(b'.abc', entry.name)
        self.assertEqual(os.fsencode(self.abc_path), entry.path)
        self.assertEqual(os.fsencode(self.abc_path), os.fspath(entry))

        # trailing slash
        with os.scandir(os.fsencode(TEST_FULL_PATH1 + '/')) as dir:
            self.assertEqual(os.fsencode(self.abc_path), next(dir).path)

    def test_scandir_entry_path_bufferlike(self):
        with os.scandir(array.array('B', os.fsencode(TEST_FULL_PATH1))) as dir:
            entry = next(dir)
        self.assertEqual("<DirEntry b'.abc'>", repr(entry))
        self.assertEqual(b'.abc', entry.name)
        self.assertEqual(os.fsencode(self.abc_path), entry.path)
        self.assertEqual(os.fsencode(self.abc_path), os.fspath(entry))

    def test_scandir_entry_path_fd(self):
        with open(TEST_FULL_PATH1, 0) as fd:
            with os.scandir(fd) as dir:
                entry = next(dir)
            # using fd instead of path returns strings and path is the same as name
            self.assertEqual("<DirEntry '.abc'>", repr(entry))
            self.assertEqual('.abc', entry.name)
            self.assertEqual('.abc', entry.path)
            self.assertEqual('.abc', os.fspath(entry))

    def test_scandir_entry_inode(self):
        sr = os.stat(self.abc_path)
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        self.assertEqual('.abc', entry.name)
        self.assertEqual(sr.st_ino, entry.inode())

    def test_scandir_entry_stat(self):
        orig_stat_result = os.stat(self.abc_path)
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        self.assertEqual(orig_stat_result, entry.stat())

        # DirEntry.stat must be cached
        with open(self.abc_path, os.O_WRONLY) as fd:
            os.write(fd, b'x')
        self.assertEqual(orig_stat_result, entry.stat())

    def test_scandir_entry_type(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        self.assertTrue(entry.is_file())
        self.assertFalse(entry.is_dir())
        self.assertFalse(entry.is_symlink())
        self.assertTrue(entry.is_file(follow_symlinks=False))
        self.assertFalse(entry.is_dir(follow_symlinks=False))

    def test_kw_only(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        with self.assertRaises(TypeError):
            entry.stat(True)
        with self.assertRaises(TypeError):
            entry.is_file(True)
        with self.assertRaises(TypeError):
            entry.is_dir(True)

    def test_stat_error_msg(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        os.unlink(self.abc_path)
        with self.assertRaisesRegex(FileNotFoundError, r"\[Errno 2\] [^:]+: '" + self.abc_path + "'"):
            entry.stat()

    def test_stat_error_msg_bytes(self):
        with os.scandir(os.fsencode(TEST_FULL_PATH1)) as dir:
            entry = next(dir)
        os.unlink(self.abc_path)
        with self.assertRaisesRegex(FileNotFoundError, r"\[Errno 2\] [^:]+: b'" + self.abc_path + "'"):
            entry.stat()

    def test_stat_uses_lstat_cache(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        stat_res = entry.stat(follow_symlinks=False)
        os.unlink(self.abc_path)
        self.assertEqual(stat_res, entry.stat(follow_symlinks=True))

    def test_listdir(self):
        self.assertEqual(['.abc'], os.listdir(TEST_FULL_PATH1))
        self.assertEqual([b'.abc'], os.listdir(os.fsencode(TEST_FULL_PATH1)))

    def test_listdir_default_arg(self):
        lst = os.listdir()
        self.assertFalse('.' in lst)
        self.assertFalse('..' in lst)


class ScandirSymlinkToFileTests(unittest.TestCase):

    def setUp(self):
        os.mkdir(TEST_FULL_PATH1)
        os.close(os.open(TEST_FULL_PATH2, os.O_WRONLY | os.O_CREAT))
        self.link_path = os.path.join(TEST_FULL_PATH1, 'abc')
        os.symlink(TEST_FULL_PATH2, self.link_path)
        self.symlink_inode = os.stat(self.link_path, follow_symlinks=False).st_ino
        self.target_inode = os.stat(TEST_FULL_PATH2).st_ino

    def tearDown(self):
        os.unlink(self.link_path)
        os.rmdir(TEST_FULL_PATH1)
        try:
            os.unlink(TEST_FULL_PATH2)
        except FileNotFoundError:
            pass

    def test_scandir_symlink_to_file_basic(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        self.assertTrue(entry.is_symlink())
        self.assertTrue(entry.is_file())
        self.assertFalse(entry.is_dir())
        self.assertTrue(entry.is_file(follow_symlinks=True))
        self.assertFalse(entry.is_dir(follow_symlinks=True))
        self.assertFalse(entry.is_file(follow_symlinks=False))
        self.assertFalse(entry.is_dir(follow_symlinks=False))

        self.assertEqual(self.target_inode, entry.stat().st_ino)
        self.assertEqual(self.target_inode, entry.stat(follow_symlinks=True).st_ino)
        self.assertEqual(self.symlink_inode, entry.stat(follow_symlinks=False).st_ino)

    def test_scandir_symlink_to_file_cached(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        stat_res_true = entry.stat(follow_symlinks=True)
        stat_res_false = entry.stat(follow_symlinks=False)
        # both stat results must be cached
        with open(TEST_FULL_PATH2, os.O_WRONLY) as fd:
            os.write(fd, b'x')
        os.unlink(self.link_path)
        os.close(os.open(self.link_path, os.O_WRONLY | os.O_CREAT))
        self.assertEqual(stat_res_true, entry.stat(follow_symlinks=True))
        self.assertEqual(stat_res_false, entry.stat(follow_symlinks=False))

    def test_scandir_symlink_to_file_file_not_found(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        os.unlink(TEST_FULL_PATH2)
        self.assertFalse(entry.is_file(follow_symlinks=True))


class ScandirSymlinkToDirTests(unittest.TestCase):

    def setUp(self):
        os.mkdir(TEST_FULL_PATH1)
        os.mkdir(TEST_FULL_PATH2)
        self.link_path = os.path.join(TEST_FULL_PATH1, 'abc')
        os.symlink(TEST_FULL_PATH2, self.link_path, target_is_directory=True)
        self.symlink_inode = os.stat(self.link_path, follow_symlinks=False).st_ino
        self.target_inode = os.stat(TEST_FULL_PATH2).st_ino

    def tearDown(self):
        os.unlink(self.link_path)
        os.rmdir(TEST_FULL_PATH1)
        try:
            os.rmdir(TEST_FULL_PATH2)
        except FileNotFoundError:
            pass

    def test_scandir_symlink_to_dir_basic(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        self.assertTrue(entry.is_symlink())
        self.assertFalse(entry.is_file())
        self.assertTrue(entry.is_dir())
        self.assertFalse(entry.is_file(follow_symlinks=True))
        self.assertTrue(entry.is_dir(follow_symlinks=True))
        self.assertFalse(entry.is_file(follow_symlinks=False))
        self.assertFalse(entry.is_dir(follow_symlinks=False))

        self.assertEqual(self.target_inode, entry.stat().st_ino)
        self.assertEqual(self.target_inode, entry.stat(follow_symlinks=True).st_ino)
        self.assertEqual(self.symlink_inode, entry.stat(follow_symlinks=False).st_ino)

    def test_scandir_symlink_to_dir_file_not_found(self):
        with os.scandir(TEST_FULL_PATH1) as dir:
            entry = next(dir)
        os.rmdir(TEST_FULL_PATH2)
        self.assertFalse(entry.is_dir(follow_symlinks=True))


class UtimeErrorsTests(unittest.TestCase):

    def test_utime_both_specified(self):
        with self.assertRaisesRegex(ValueError, "utime: you may specify either 'times' or 'ns' but not both"):
            os.utime(TEST_FULL_PATH1, (1, 2), ns=(3, 4))
        with self.assertRaisesRegex(ValueError, "utime: you may specify either 'times' or 'ns' but not both"):
            os.utime(TEST_FULL_PATH1, (1, 2), ns=None)

    def test_utime_times_not_a_tuple(self):
        with self.assertRaisesRegex(TypeError, "utime: 'times' must be either a tuple of two ints or None"):
            os.utime(TEST_FULL_PATH1, '')

    def test_utime_ns_not_a_tuple(self):
        with self.assertRaisesRegex(TypeError, "utime: 'ns' must be a tuple of two ints"):
            os.utime(TEST_FULL_PATH1, ns='')
        with self.assertRaisesRegex(TypeError, "utime: 'ns' must be a tuple of two ints"):
            os.utime(TEST_FULL_PATH1, ns=None)
        with self.assertRaisesRegex(TypeError, "utime: 'ns' must be a tuple of two ints"):
            os.utime(TEST_FULL_PATH1, None, ns='')
        with self.assertRaisesRegex(TypeError, "utime: 'ns' must be a tuple of two ints"):
            os.utime(TEST_FULL_PATH1, None, ns=None)

    def test_utime_fd_symlinks(self):
        with self.assertRaisesRegex(ValueError, "utime: cannot use fd and follow_symlinks together"):
            os.utime(1, follow_symlinks=False)
        with self.assertRaisesRegex(ValueError, "utime: cannot use fd and follow_symlinks together"):
            os.utime(1, None, follow_symlinks=False)
        with self.assertRaisesRegex(ValueError, "utime: cannot use fd and follow_symlinks together"):
            os.utime(1, (1, 2), follow_symlinks=False)
        with self.assertRaisesRegex(ValueError, "utime: cannot use fd and follow_symlinks together"):
            os.utime(1, ns=(1, 2), follow_symlinks=False)

    def test_utime_fd_dirfd(self):
        with self.assertRaisesRegex(ValueError, "utime: can't specify dir_fd without matching path"):
            os.utime(1, dir_fd=2)
        with self.assertRaisesRegex(ValueError, "utime: can't specify dir_fd without matching path"):
            os.utime(1, None, dir_fd=2, follow_symlinks=False)
        with self.assertRaisesRegex(ValueError, "utime: can't specify dir_fd without matching path"):
            os.utime(1, (1, 2), dir_fd=2, follow_symlinks=False)
        with self.assertRaisesRegex(ValueError, "utime: can't specify dir_fd without matching path"):
            os.utime(1, ns=(1, 2), dir_fd=2)


class RenameTests(unittest.TestCase):

    def setUp(self):
        os.close(os.open(TEST_FULL_PATH1, os.O_WRONLY | os.O_CREAT))
        os.mkdir(TEST_FULL_PATH2)
        self.dst_name = "xyz"
        self.dst_full_path = os.path.join(TEST_FULL_PATH2, self.dst_name)

    def tearDown(self):
        try:
            os.unlink(TEST_FULL_PATH1)
        except (FileNotFoundError, NotADirectoryError):
            pass
        try:
            os.unlink(self.dst_full_path)
        except (FileNotFoundError, NotADirectoryError):
            pass
        try:
            os.rmdir(TEST_FULL_PATH2)
        except (FileNotFoundError, NotADirectoryError):
            pass

    def test_rename_simple(self):
        os.rename(TEST_FULL_PATH1, self.dst_full_path)
        with self.assertRaises(FileNotFoundError):
            os.stat(TEST_FULL_PATH1)
        os.stat(self.dst_full_path)
        os.replace(self.dst_full_path, TEST_FULL_PATH1)
        os.stat(TEST_FULL_PATH1)
        with self.assertRaises(FileNotFoundError):
            os.stat(self.dst_full_path)

    def test_rename_with_dirfd(self):
        with open(TEMP_DIR, 0) as tmp_fd:
            os.rename(TEST_FILENAME1, self.dst_full_path, src_dir_fd=tmp_fd)
        with self.assertRaises(FileNotFoundError):
            os.stat(TEST_FULL_PATH1)
        os.stat(self.dst_full_path)
        with open(TEMP_DIR, 0) as tmp_fd:
            os.rename(self.dst_full_path, TEST_FILENAME1, dst_dir_fd=tmp_fd)
        os.stat(TEST_FULL_PATH1)
        with self.assertRaises(FileNotFoundError):
            os.stat(self.dst_full_path)

    def test_rename_replace_err_msg(self):
        with self.assertRaisesRegex(TypeError, 'rename'):
            os.rename(3.14, TEST_FILENAME1)
        with self.assertRaisesRegex(TypeError, 'replace'):
            os.replace(TEST_FILENAME1, 3.14)


class SysconfTests(unittest.TestCase):
    def test_sysconf_names(self):
        self.assertIn('SC_CLK_TCK', os.sysconf_names)

    def test_sysconf(self):
        self.assertGreaterEqual(os.sysconf('SC_CLK_TCK'), 0)
        with self.assertRaisesRegex(TypeError, 'strings or integers'):
            os.sysconf(object())
        with self.assertRaisesRegex(ValueError, 'unrecognized'):
            os.sysconf("nonexistent")
        with self.assertRaisesRegex(OSError, "Invalid argument"):
            os.sysconf(123456)


if __name__ == '__main__':
    unittest.main()
