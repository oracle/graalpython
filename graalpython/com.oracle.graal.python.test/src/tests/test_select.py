# Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import select
import tempfile
import unittest

PREFIX = 'select_graalpython_test'
TEMP_DIR = tempfile.gettempdir()
TEST_FILENAME = f'{PREFIX}_{os.getpid()}_tmp1'
TEST_FILENAME_FULL_PATH = os.path.join(TEMP_DIR, TEST_FILENAME)

try:
    __graalpython__.posix_module_backend()
except:
    class GP:
        def posix_module_backend(self):
            return 'cpython'
    __graalpython__ = GP()

class SelectTests(unittest.TestCase):
    def test_select_raises_on_object_with_no_fileno_func(self):
        self.assertRaises(TypeError, select.select, 'abc', [], [], 0)

    def test_select_uses_dunder_index_of_timeout_obj(self):
        class MyVal:
            def __init__(self):
                self.called_index = 0
            def __index__(self):
                self.called_index += 1
                return 1

        v = MyVal()
        select.select([], [], [], v)
        assert v.called_index == 1

    def test_select_timeout_arg_validation(self):
        self.assertRaises(ValueError, select.select, [], [], [], float("nan"))
        self.assertRaises(OverflowError, select.select, [], [], [], 1234567891234567891234567.5)
        # Still in long range, but once converted to ns, it will overflow
        self.assertRaises(OverflowError, select.select, [], [], [], 9223372036854775806)

    @unittest.skipUnless(__graalpython__.posix_module_backend() != 'java',
                         'The java backend does not support select for ordinary files, only sockets.')
    def test_select_result_duplicate_fds_preserve_objs_order(self):
        class F:
            def __init__(self, fd):
                self.fd = fd
            def fileno(self):
                return self.fd

        os.close(os.open(TEST_FILENAME_FULL_PATH, os.O_WRONLY | os.O_CREAT))
        with open(TEST_FILENAME_FULL_PATH, 'r') as f:
            r_pipe, w_pipe = os.pipe()
            try:
                # the pipe is not ready for reading, but the file should be,
                # we should get back both objects and in the right order
                fds = [F(f.fileno()), F(r_pipe), F(f.fileno())]
                res = select.select(fds, [], [], 1)
                assert res == ([fds[0], fds[2]], [], [])
            finally:
                os.close(r_pipe)
                os.close(w_pipe)
