# Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import builtins
import types
import unittest


class BasicTests(unittest.TestCase):

    def make_global_reader(self, name="value", **values):
        module = types.ModuleType("global_read_test")
        module.__dict__.update(values)
        exec(f"def read():\n    return {name}\n", module.__dict__)
        return module, module.read

    def warm_global_reader(self, read, expected):
        # Exercise the cached interpreter, including its quickened LOAD_GLOBAL.
        for _ in range(100):
            self.assertIs(read(), expected)

    def test_cached_global_reassignment(self):
        module, read = self.make_global_reader(value=object())
        self.warm_global_reader(read, module.value)
        for value in (42, 3.5, None, object()):
            module.value = value
            self.warm_global_reader(read, value)
            value = object()
            module.__dict__["value"] = value
            self.warm_global_reader(read, value)

    def test_cached_global_delete_and_reinsert(self):
        module, read = self.make_global_reader(value=object())
        self.warm_global_reader(read, module.value)
        del module.value
        with self.assertRaises(NameError):
            read()
        module.value = object()
        self.warm_global_reader(read, module.value)

    def test_cached_global_storage_replacement(self):
        for operation in ("clear", "non_string_key", "update_non_string_key"):
            with self.subTest(operation=operation):
                module, read = self.make_global_reader(value=object())
                self.warm_global_reader(read, module.value)
                namespace = module.__dict__
                if operation == "clear":
                    namespace.clear()
                elif operation == "non_string_key":
                    namespace[42] = "force general storage"
                else:
                    namespace.update({42: "force general storage"})
                value = object()
                namespace["value"] = value
                self.warm_global_reader(read, value)
                del namespace["value"]
                with self.assertRaises(NameError):
                    read()

    def test_cached_global_shared_code_different_globals(self):
        module, read = self.make_global_reader(value=object())
        self.warm_global_reader(read, module.value)
        other = types.ModuleType("other_globals")
        other.value = object()
        other_read = types.FunctionType(read.__code__, other.__dict__)
        plain_value = object()
        plain_read = types.FunctionType(read.__code__, {"value": plain_value})
        for _ in range(100):
            self.assertIs(other_read(), other.value)
            self.assertIs(read(), module.value)
            self.assertIs(plain_read(), plain_value)

    def test_cached_builtin_shadow_and_delete(self):
        module, read = self.make_global_reader("len")
        self.warm_global_reader(read, builtins.len)
        module.len = object()
        self.warm_global_reader(read, module.len)
        del module.len
        self.warm_global_reader(read, builtins.len)

    def test_cached_global_falls_back_to_builtin(self):
        module, read = self.make_global_reader("len", len=object())
        self.warm_global_reader(read, module.len)
        del module.len
        self.warm_global_reader(read, builtins.len)

    def test_cached_builtin_storage_replacement(self):
        for clear in (False, True):
            with self.subTest(clear=clear):
                module, read = self.make_global_reader("len")
                self.warm_global_reader(read, builtins.len)
                namespace = module.__dict__
                if clear:
                    namespace.clear()
                else:
                    namespace[42] = "force general storage"
                value = object()
                namespace["len"] = value
                self.warm_global_reader(read, value)

    def test_cached_builtin_shared_code_different_globals(self):
        module, read = self.make_global_reader("len")
        self.warm_global_reader(read, builtins.len)
        other = types.ModuleType("other_globals")
        other.len = object()
        other_read = types.FunctionType(read.__code__, other.__dict__)
        plain_value = object()
        plain_read = types.FunctionType(read.__code__, {"len": plain_value})
        for _ in range(100):
            self.assertIs(other_read(), other.len)
            self.assertIs(read(), builtins.len)
            self.assertIs(plain_read(), plain_value)

    def test_builtin_after_global_deleted_before_first_read(self):
        module, read = self.make_global_reader("len", len=object())
        # A deleted property can remain in the shape with a NO_VALUE value.
        del module.len
        self.warm_global_reader(read, builtins.len)
        module.len = object()
        self.warm_global_reader(read, module.len)

    def test_cached_builtin_reassignment_and_deletion(self):
        name = "_global_read_test_builtin"
        self.assertFalse(hasattr(builtins, name))
        module, read = self.make_global_reader(name)
        try:
            for _ in range(2):
                value = object()
                setattr(builtins, name, value)
                self.warm_global_reader(read, value)
            delattr(builtins, name)
            with self.assertRaises(NameError):
                read()
            module.__dict__[name] = object()
            self.warm_global_reader(read, module.__dict__[name])
        finally:
            if hasattr(builtins, name):
                delattr(builtins, name)

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
