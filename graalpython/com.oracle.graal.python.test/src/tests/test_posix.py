# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

# TODO replace with normal import once we remove the nfi prefixes
def wrap_modules():
    import os as _os
    import posix as _posix
    class OsWrapper:
        def __getattribute__(self, item):
            return getattr(_os, f'nfi_{item}', getattr(_os, item, None))
    class PosixWrapper:
        def __getattribute__(self, item):
            return getattr(_posix, f'nfi_{item}', getattr(_posix, item, None))
    return OsWrapper(), PosixWrapper()


os, posix = wrap_modules()

try:
    __graalpython__.posix_module_backend()
except:
    class GP:
        def posix_module_backend(self):
            return 'cpython'
    __graalpython__ = GP()


import unittest
import sys
import stat
import tempfile


PREFIX = 'graalpython_test'
TEMP_DIR = tempfile.gettempdir()

TEST_FILENAME1 = f'{PREFIX}_{os.getpid()}_tmp1'
TEST_FILENAME2 = f'{PREFIX}_{os.getpid()}_tmp2'
TEST_FULL_PATH1 = os.path.join(TEMP_DIR, TEST_FILENAME1)
TEST_FULL_PATH2 = os.path.join(TEMP_DIR, TEST_FILENAME2)


class PosixTests(unittest.TestCase):

    def test_uname(self):
        # just like cpython, a simple smoke test
        uname = posix.uname()
        self.assertRaises(TypeError, lambda: posix.uname(1))
        self.assertIsNotNone(uname.sysname)
        self.assertIsNotNone(uname.nodename)
        self.assertIsNotNone(uname.release)
        self.assertIsNotNone(uname.version)
        self.assertIsNotNone(uname.machine)

#     def test_execv(self):
#         # test creates a shell script, which again creates a file, to ensure script execution
#         # Both files are deleted again in the end
#         new_file_path, cwd = self.create_file()
#         os.system("%s -c \"import os; os.execv('%s', ['%s', 'the_input'])\"" % (sys.executable, new_file_path, new_file_path))
#         assert os.path.isfile(cwd + '/test.txt')
#         self.delete_file(new_file_path, cwd)
#
#     def test_execl(self):
#         # test creates a shell script, which again creates a file, to ensure script execution
#         # Both files are deleted again in the end
#         new_file_path, cwd = self.create_file()
#         os.system("%s -c \"import os; os.execl('%s', *['%s', 'the_input'])\"" % (sys.executable, new_file_path, new_file_path))
#         assert os.path.isfile(cwd + '/test.txt')
#         self.delete_file(new_file_path, cwd)
#
#     def test_execv_with_env(self):
#         new_file_path, cwd = self.create_file()
#         with open(new_file_path, 'w') as script:
#             script.write('#!/bin/sh\n')
#             script.write('echo $ENV_VAR > {}/test.txt\n'.format(cwd))
#         os.system("%s -c \"import os; os.environ['ENV_VAR']='the_text'; os.execv('%s', ['%s', 'the_input'])\"" % (sys.executable, new_file_path, new_file_path))
#         assert os.path.isfile(cwd + '/test.txt')
#         with open(cwd+'/test.txt', 'r') as result:
#             assert 'the_text' in result.readline()
#         self.delete_file(new_file_path, cwd)
#
#    def test_path_respecialization(self):
#        # regression test for https://github.com/graalvm/graalpython/issues/124
#        from pathlib import PurePath
#        p = PurePath(".")
#        for path in [p, "."]:
#            os.scandir(path)
#
#     def create_file(self):
#         cwd = os.getcwd()
#         new_file_path = os.path.join(cwd , 'myscript.sh')
#         with open(new_file_path, 'w') as script:
#             script.write('#!/bin/sh\n')
#             script.write("echo \"something echo with\" $1 > {}/test.txt\n".format(cwd))
#             script.write('echo this is an output\n')
#         assert os.path.isfile(new_file_path)
#         st = os.stat(new_file_path)
#         os.chmod(new_file_path, st.st_mode | stat.S_IEXEC)
#         return new_file_path, cwd

    def test_empty_stat(self):
        with self.assertRaises(FileNotFoundError):
            os.stat('')

    # def delete_file(self, new_file_path, cwd):
    #     os.remove(new_file_path)
    #     os.remove(cwd + '/test.txt')

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
        tmp_fd = os.open(TEMP_DIR, 0)
        try:
            os.mkdir(TEST_FILENAME1, dir_fd=tmp_fd)
            try:
                self.assertTrue(stat.S_ISDIR(os.stat(TEST_FULL_PATH1).st_mode))
            finally:
                os.rmdir(TEST_FILENAME1, dir_fd=tmp_fd)
        finally:
            os.close(tmp_fd)

    def test_umask(self):
        orig = os.umask(0o22)
        self.assertEqual(0o22, os.umask(orig))


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
        fd2 = os.dup(self.fd)
        try:
            self.assertFalse(os.get_inheritable(fd2))
            os.set_inheritable(fd2, True)
            self.assertTrue(os.get_inheritable(fd2))
        finally:
            os.close(fd2)
        fd2 = os.dup2(self.fd, fd2, True)
        try:
            self.assertTrue(os.get_inheritable(fd2))
            os.set_inheritable(fd2, False)
            self.assertFalse(os.get_inheritable(fd2))
        finally:
            os.close(fd2)

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

    def test_stat(self):
        sr1 = os.stat(TEST_FULL_PATH1)
        self.assertEqual(int(sr1.st_atime), sr1[7])
        self.assertEqual(sr1.st_atime_ns/1000000000, sr1.st_atime)
        self.assertEqual(0, sr1.st_size)
        self.assertEqual(sr1.st_ino, os.stat(TEST_FULL_PATH2).st_ino)       # follow_symlinks = True
        sr2 = os.stat(TEST_FULL_PATH2, follow_symlinks=False)
        self.assertNotEqual(sr1.st_ino, sr2.st_ino)

    def test_stat_fd(self):
        inode = os.stat(TEST_FULL_PATH1).st_ino
        fd1 = os.open(TEST_FULL_PATH2, 0)   # TEST_FULL_PATH2 is a symlink to TEST_FULL_PATH1
        try:
            self.assertEqual(inode, os.stat(fd1).st_ino)
            with self.assertRaises(ValueError, msg="stat: cannot use fd and follow_symlinks together"):
                os.stat(fd1, follow_symlinks=False)
        finally:
            os.close(fd1)

    def test_stat_dirfd(self):
        inode = os.stat(TEST_FULL_PATH1).st_ino
        self.assertEqual(inode, os.stat(TEST_FILENAME1, dir_fd=self.tmp_fd).st_ino)
        with self.assertRaises(ValueError, msg="stat: can't specify dir_fd without matching path"):
            os.stat(0, dir_fd=self.tmp_fd)
        with self.assertRaises(ValueError, msg="stat: can't specify dir_fd without matching path"):
            os.stat(0, dir_fd=self.tmp_fd, follow_symlinks=False)

    def test_lstat(self):
        inode = os.stat(TEST_FULL_PATH2, follow_symlinks=False).st_ino
        self.assertEqual(inode, os.lstat(TEST_FULL_PATH2).st_ino)   # lstat does not follow symlink
        self.assertEqual(inode, os.lstat(TEST_FILENAME2, dir_fd=self.tmp_fd).st_ino)
        with self.assertRaises(TypeError, msg="lstat: path should be string, bytes or os.PathLike, not int"):
            os.lstat(self.tmp_fd)

    def test_fstat(self):
        inode = os.stat(TEST_FULL_PATH1).st_ino
        fd1 = os.open(TEST_FULL_PATH2, 0)           # follows symlink
        try:
            self.assertEqual(inode, os.fstat(fd1).st_ino)
        finally:
            os.close(fd1)


class ChdirTests(unittest.TestCase):

    def setUp(self):
        self.old_wd = os.getcwd()

    def tearDown(self):
        os.chdir(self.old_wd)

    def test_chdir(self):
        os.chdir(TEMP_DIR)
        os.chdir(self.old_wd)
        self.assertEqual(os.fsencode(self.old_wd), os.getcwdb())
        os.chdir(os.fsencode(self.old_wd))
        self.assertEqual(self.old_wd, os.getcwd())

    def test_chdir_fd(self):
        os.chdir(TEMP_DIR)
        fd = os.open(self.old_wd, 0)
        try:
            os.chdir(fd)
            self.assertEqual(self.old_wd, os.getcwd())
        finally:
            os.close(fd)

    def test_fchdir(self):
        fd = os.open(self.old_wd, 0)
        try:
            os.fchdir(fd)
            self.assertEqual(os.fsencode(self.old_wd), os.getcwdb())
        finally:
            os.close(fd)


if __name__ == '__main__':
    unittest.main()
