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

import unittest
import os
import sys
import posix
import stat


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
        with open(new_file_path, 'w') as script:
            script.write('#!/bin/sh\n')
            script.write('echo $ENV_VAR > {}/test.txt\n'.format(cwd))
        os.system("%s -c \"import os; os.environ['ENV_VAR']='the_text'; os.execv('%s', ['%s', 'the_input'])\"" % (sys.executable, new_file_path, new_file_path))
        assert os.path.isfile(cwd + '/test.txt')
        with open(cwd+'/test.txt', 'r') as result:
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
        with open(new_file_path, 'w') as script:
            script.write('#!/bin/sh\n')
            script.write("echo \"something echo with\" $1 > {}/test.txt\n".format(cwd))
            script.write('echo this is an output\n')
        assert os.path.isfile(new_file_path)
        st = os.stat(new_file_path)
        os.chmod(new_file_path, st.st_mode | stat.S_IEXEC)
        return new_file_path, cwd

    def delete_file(self, new_file_path, cwd):
        os.remove(new_file_path)
        os.remove(cwd + '/test.txt')
