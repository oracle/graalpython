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
import pickle

class TestPickle(unittest.TestCase):

    def test_builtin(self):
        self.pickle_unpickle(len)
        import sys
        self.pickle_unpickle(sys.getrecursionlimit)

    def test_local(self):
        self.pickle_unpickle("test")
        myvar = 10
        self.pickle_unpickle(myvar)

    def pickle_unpickle(self, obj):
        b_obj = pickle.dumps(obj, protocol=0)
        r_obj = pickle.loads(b_obj)
        self.assertEqual(r_obj, obj)

    def test_teeiterator(self):
        import itertools
        teeit = itertools.tee([i for i in range(1, 20)])[0]
        [next(teeit) for i in range(1, 16)]
        b_obj = pickle.dumps(teeit, protocol=0)
        teeit2 = pickle.loads(b_obj)
        assert [16,17,18,19] == [next(teeit2) for i in range(1, 5)]
        assert [16,17,18,19] == [next(teeit) for i in range(1, 5)]

if __name__ == '__main__':
    unittest.main()
