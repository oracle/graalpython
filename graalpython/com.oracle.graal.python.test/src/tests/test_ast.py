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
import _ast
import ast
import sys


class BytesSubclass(bytes):
    pass


class StrSubclass(str):
    pass


class AstTest(unittest.TestCase):

    def test_init_arg_count(self):
        class Node1(_ast.AST):
            _fields = ('a',)

        class Node2(_ast.AST):
            _fields = ('a', 'b')

        with self.assertRaisesRegex(TypeError, "AST constructor takes at most 0 positional arguments"):
            _ast.AST(42)
        with self.assertRaisesRegex(TypeError, "Node1 constructor takes at most 1 positional argument"):
            Node1(41, 42)
        with self.assertRaisesRegex(TypeError, "Node2 constructor takes at most 2 positional arguments"):
            Node2(41, 42, 43)

    def test_init_fields_can_be_list(self):
        class Node(_ast.AST):
            _fields = ['f1']

        n = Node(42)
        self.assertEqual(42, n.f1)

    def test_init_fields_must_be_sequence(self):
        class Node(_ast.AST):
            _fields = 42

        with self.assertRaises(TypeError):
            Node()

    def test_init_accepts_extra_kwargs(self):
        class Node(_ast.AST):
            _fields = ('a', 'b')

        n = Node(41, b=42, c=43)
        self.assertEqual(41, n.a)
        self.assertEqual(42, n.b)
        self.assertEqual(43, n.c)
        self.assertEqual('abc', _ast.AST(f='abc').f)

    def test_init_rejects_duplicate(self):
        class Node(_ast.AST):
            _fields = ('a', 'b')

        with self.assertRaisesRegex(TypeError, "Node got multiple values for argument 'a'"):
            Node(41, b=42, a=43)

    def test_reduce(self):
        class Node(_ast.AST):
            _fields = ('a', 'b')

        self.assertEqual((Node, (), {'a': 41, 'b': 42}), Node(41, 42).__reduce__())
        self.assertEqual((_ast.AST, (), {}), _ast.AST().__reduce__())

    def test_dict_can_be_set(self):
        class Node(_ast.AST):
            _fields = ('a', 'b')

        n = _ast.AST()
        n.__dict__ = {'x': 14}
        self.assertEqual((_ast.AST, (), {'x': 14}), n.__reduce__())

        n = Node(41, 42)
        n.__dict__ = {'x': 14}
        self.assertEqual((Node, (), {'x': 14}), n.__reduce__())

    def test_dict_must_be_dict(self):
        with self.assertRaisesRegex(TypeError, "__dict__ must be set to a dictionary, not a 'int'"):
            _ast.AST().__dict__ = 42

    def test_dict_cannot_be_deleted(self):
        with self.assertRaises(TypeError):
            del _ast.AST().__dict__

    def test_non_string_fields(self):
        class Node(_ast.AST):
            _fields = (42, [], 'a')

        self.assertEqual(1, Node(a=1).a)
        with self.assertRaisesRegex(TypeError, "must be string"):
            Node(1)

    def test_bytes_constant_kind(self):
        src = "x = u'abc'"
        tree = ast.parse(src)
        tree.body[0].value.kind = b'u'
        compile(tree, '<string>', 'exec')   # nothing to assert, it just must not crash/throw

        with self.assertRaisesRegex(TypeError, "AST string must be of type str"):
            tree.body[0].value.kind = BytesSubclass(b'u')
            compile(tree, '<string>', 'exec')
        with self.assertRaisesRegex(TypeError, "AST string must be of type str"):
            tree.body[0].value.kind = StrSubclass('u')
            compile(tree, '<string>', 'exec')

    def test_bytes_type_comment(self):
        src = "x = 42 # type: int"
        tree = ast.parse(src, type_comments=True)
        tree.body[0].type_comment = b'int'
        compile(tree, '<string>', 'exec')   # nothing to assert, it just must not crash/throw

        with self.assertRaisesRegex(TypeError, "AST string must be of type str"):
            tree.body[0].type_comment = BytesSubclass(b'int')
            compile(tree, '<string>', 'exec')
        with self.assertRaisesRegex(TypeError, "AST string must be of type str"):
            tree.body[0].type_comment = StrSubclass('int')
            compile(tree, '<string>', 'exec')

    def test_bytes_type_ignore(self):
        src = "x = 42 # type: ignore abc"
        tree = ast.parse(src, type_comments=True)
        tree.type_ignores[0].tag = b' abc'
        compile(tree, '<string>', 'exec')   # nothing to assert, it just must not crash/throw

        with self.assertRaisesRegex(TypeError, "AST string must be of type str"):
            tree.type_ignores[0].tag = BytesSubclass(b' abc')
            compile(tree, '<string>', 'exec')
        with self.assertRaisesRegex(TypeError, "AST string must be of type str"):
            tree.type_ignores[0].tag = StrSubclass(' abc')
            compile(tree, '<string>', 'exec')

    @unittest.skipIf(sys.implementation.name == 'cpython', "CPython crashes")
    def test_unparse_bytes_constant_kind(self):
        src = "from __future__ import annotations\ndef f(x: u'abc' = 42): pass"
        tree = ast.parse(src)
        tree.body[1].args.args[0].annotation.kind = b'u'
        vars = {}
        exec(compile(tree, '<string>', 'exec'), vars)
        self.assertEqual("u'abc'", vars['f'].__annotations__['x'])


if __name__ == '__main__':
    unittest.main()
