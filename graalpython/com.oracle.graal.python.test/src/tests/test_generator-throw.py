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


def gen():
    try:
        yield 1
    except OSError:
        yield "gotya"
    yield 2


class GeneratorThrowTests(unittest.TestCase):
    def test_throw_unstarted(self):
        g = gen()
        self.assertRaises(OverflowError, lambda: g.throw(OverflowError))

    def test_throw_with_type(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError)
        except OverflowError as e:
            self.assertEqual(e.args, ())
        else:
            self.fail("Exception not thrown")

    def test_throw_with_type_exception(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError, OverflowError("value"))
        except OverflowError as e:
            self.assertEqual(e.args, ("value",))
        else:
            self.fail("Exception not thrown")

    def test_throw_with_exception(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError("value"))
        except OverflowError as e:
            self.assertEqual(e.args, ("value",))
        else:
            self.fail("Exception not thrown")

    def test_throw_with_type_value(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError, "value")
        except OverflowError as e:
            self.assertEqual(e.args, ("value",))
        else:
            self.fail("Exception not thrown")

    def test_throw_with_type_value_tuple(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError, ("1", "2"))
        except OverflowError as e:
            self.assertEqual(e.args, ("1", "2"))
        else:
            self.fail("Exception not thrown")

    def test_throw_with_type_value_exception_subclass(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError, NameError("value"))
        except OverflowError as e:
            self.assertEqual(type(e.args[0]), NameError)
        else:
            self.fail("Exception not thrown")

    def test_throw_with_traceback(self):
        try:
            raise NameError
        except Exception as e:
            tb = e.__traceback__
        g = gen()
        next(g)
        try:
            g.throw(OverflowError, OverflowError("value"), tb)
        except OverflowError as e:
            self.assertEqual(e.args, ("value",))
            # The traceback is tested in test_traceback
        else:
            self.fail("Exception not thrown")

    def test_throw_and_catch(self):
        g = gen()
        self.assertEqual(next(g), 1)
        self.assertEqual(g.throw(OSError), "gotya")
        self.assertEqual(next(g), 2)
        self.assertRaises(OSError, lambda: g.throw(OSError))
