# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import posix
import sys
import unittest


class StructSeqTests(unittest.TestCase):

    def test_constructor_basic(self):
        x = posix.terminal_size((42, 3.14))
        self.assertTrue(isinstance(x, posix.terminal_size))
        self.assertEqual(2, len(x))
        self.assertEqual(42, x[0])
        self.assertEqual(42, x.columns)
        self.assertEqual(3.14, x[1])
        self.assertEqual(3.14, x.lines)
        self.assertEqual('os.terminal_size(columns=42, lines=3.14)', repr(x))

    def test_constructor_with_opt(self):
        self.assertRaises(TypeError, posix.terminal_size, range(9))
        self.assertRaises(TypeError, posix.terminal_size, range(17))
        x = posix.stat_result(range(10))
        self.assertEqual('os.stat_result(st_mode=0, st_ino=1, st_dev=2, st_nlink=3, st_uid=4, st_gid=5, st_size=6, st_atime=7, st_mtime=8, st_ctime=9)', repr(x))
        x = posix.stat_result(range(16))
        self.assertEqual('os.stat_result(st_mode=0, st_ino=1, st_dev=2, st_nlink=3, st_uid=4, st_gid=5, st_size=6, st_atime=7, st_mtime=8, st_ctime=9)', repr(x))
        x = posix.stat_result(range(10), {'st_atime': 42, 'st_atime_ns': 43, 'abc': 44})
        self.assertEqual(7, x[7])
        self.assertEqual(42, x.st_atime)
        self.assertEqual(43, x.st_atime_ns)

    def test_constructor_err(self):
        with self.assertRaisesRegex(TypeError, r'os.terminal_size\(\) takes a dict as second arg, if any'):
            posix.terminal_size((), "")
        self.assertRaises(TypeError, posix.terminal_size)
        with self.assertRaisesRegex(TypeError, r'constructor requires a sequence'):
            posix.terminal_size(42)
        with self.assertRaisesRegex(TypeError, r'os.terminal_size\(\) takes a 2-sequence \(0-sequence given\)'):
            posix.terminal_size(())
        with self.assertRaisesRegex(TypeError, r'os.stat_result\(\) takes an at least 10-sequence \(1-sequence given\)'):
            posix.stat_result((1,))
        with self.assertRaisesRegex(TypeError, r'os.stat_result\(\) takes an at most \d+-sequence \(30-sequence given\)'):
            posix.stat_result((1,) * 30)
        with self.assertRaisesRegex(TypeError, r'cannot create \'sys.flags\' instances'):
            type(sys.flags)()

    def test_no_subclass(self):
        with self.assertRaises(TypeError):
            class Subclass(posix.terminal_size):
                pass

    def test_reduce(self):
        x = posix.stat_result(range(10), {'st_atime': 42, 'st_atime_ns': 43, 'abc': 44})
        r = x.__reduce__()
        self.assertEqual(posix.stat_result, r[0])
        self.assertEqual(tuple(range(10)), r[1][0])
        self.assertEqual(42, r[1][1]['st_atime'])
        self.assertEqual(None, r[1][1]['st_mtime_ns'])
        self.assertFalse('abc' in r[1][1])
        x = posix.terminal_size((42, 3.14))
        self.assertEqual((posix.terminal_size, ((42, 3.14), {})), x.__reduce__())

    def test_doc(self):
        self.assertEqual('A tuple of (columns, lines) for holding terminal window size', posix.terminal_size.__doc__)
        self.assertEqual('width of the terminal window in characters', posix.terminal_size.columns.__doc__)


if __name__ == '__main__':
    unittest.main()
