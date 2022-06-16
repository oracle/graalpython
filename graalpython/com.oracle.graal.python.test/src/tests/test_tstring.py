# Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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


class ss(str):
    pass


ts1 = 'abc'
ts2 = 'x☏yz'
ts3 = '\U00010400'      # surrogates: D801 DC00
ss1 = ss('abc')
ss2 = ss('x☏yz')
ss3 = ss('\U00010400')


class TruffleStringTests(unittest.TestCase):

    def test_hash(self):
        self.assertEqual(hash(ts3), hash(ss3))
        self.assertEqual(hash(ts1), ts1.__hash__())
        self.assertEqual(hash(ss1), ss1.__hash__())

    def test_len(self):
        for exp_len, slist in {3: (ts1, ss1), 4: (ts2, ss2), 1: (ts3, ss3)}.items():
            for s in slist:
                self.assertEqual(exp_len, len(s))
                self.assertEqual(exp_len, s.__len__())

    def test_getnewargs(self):
        self.assertEqual(('abc',), ts1.__getnewargs__())
        self.assertEqual(('abc',), ss1.__getnewargs__())
        self.assertIsNot(ts1, ts1.__getnewargs__()[0])
        self.assertIsNot(ss1, ss1.__getnewargs__()[0])

    def test_contains(self):
        self.assertIn('b', ts1)
        self.assertIn(ss('b'), ts1)
        self.assertIn('b', ss1)
        self.assertIn(ss('b'), ss1)
        self.assertFalse('\udc00' in ts3)
        self.assertFalse('\udc00' in ss3)

    def test_dict(self):
        key = "\x00"
        self.assertEqual(1, {ss(key): 1}[key])

    def test_lst(self):
        self.assertEqual([ts3], list(ts3))

    def test_compare(self):
        s1 = '\ufb00'
        s2 = ts3
        self.assertTrue(s1 < s2)
        self.assertTrue((s1, ) < (s2, ))
        self.assertTrue(s1.__lt__(s2))

    def test_collections(self):
        self.assertEqual(3, len(dict.fromkeys("a\U00010400b")))
        self.assertEqual(3, len(list("a\U00010400b")))
        self.assertEqual(3, len(tuple("a\U00010400b")))
        self.assertEqual(ts3, list("a\U00010400b")[1])
        self.assertEqual(ts3, tuple("a\U00010400b")[1])

    def test_str_iter(self):
        self.assertEqual(ts3, next(iter(ts3)))
        self.assertEqual(ts3, next(reversed(ts3)))

    def test_surrogates(self):
        self.assertFalse('\ud801' + '\udc00' == '\U00010400')


if __name__ == '__main__':
    unittest.main()
