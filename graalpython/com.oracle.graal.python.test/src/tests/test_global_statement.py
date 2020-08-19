# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
from test.support import run_unittest

class BasicTests(unittest.TestCase):

    def test_in_local(self):
        loc = {}
        glob = {}
        exec("""if 1:
            x = 3
            def f():
              x
        """, glob, loc)
        # normally the variables are placed in the local
        self.assertEqual(loc['x'], 3)

    def test_put_to_global(self):
        loc = {}
        glob = {}
        exec("""if 1:
            x = 3
            def f():
              global x
        """, glob, loc)
        # if a variable is marked as global, the variable is not in the local, but in global directory
        self.assertEqual(glob['x'], 3)

    def test_global_in_inner_scope(self):
        loc = {}
        glob = {'self': self}
        exec("""if 1:
            x = 4
            def f():
              self.assertEqual(x, 4)
              def g():
                global x
            f()
        """, glob, loc)
        self.assertEqual(glob['x'], 4)

    def test_get_global_in_inner_scope(self):
        loc = {}
        glob = {'self': self}
        # if some scope declare a variable as global, then the inner scopes read
        # it as global as well. 
        exec("""if 1:
            x = 5
            def f():
              x = 1
              self.assertEqual(x, 1)
              def g():
                global x
                def h():
                  self.assertEqual(x, 5)
                h()
              g()
            f()
        """, glob, loc)
        self.assertEqual(glob['x'], 5)

    def test_set_local_in_inner_scope(self):
        loc = {}
        glob = {'self': self}
        exec("""if 1:
            x = 6
            def f():
              x = 1
              self.assertEqual(x, 1)
              def g():
                global x
                def h():
                  x = 11
                  self.assertEqual(x, 11)
                h()
              g()
            f()
        """, glob, loc)
        self.assertEqual(glob['x'], 6)


if __name__ == '__main__':
    unittest.main()
