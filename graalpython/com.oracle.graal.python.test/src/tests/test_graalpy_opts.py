# Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import sys
import unittest

if sys.implementation.name == "graalpy" and not __graalpython__.is_forced_uncached_interpreter:

    skipUnlessSingleContext = unittest.skipUnless(__graalpython__.is_single_context, "requires single-context mode")

    def assert_contains_bytecode(fun, bytecode_str):
        bytecode = __graalpython__.dis(fun)
        assert bytecode_str in __graalpython__.dis(fun), bytecode


    def test_read_name_quickening_local():
        code = compile("value = 42\nassert value == 42", "<test>", "exec")
        for _ in range(5):
            exec(code)
        assert_contains_bytecode(code, "ReadName$LocalFastPath")


    def test_read_global_quickening_builtin():
        def tester():
            return len((1, 2, 3))

        for _ in range(5):
            assert tester() == 3
        assert_contains_bytecode(tester, "ReadGlobal$ReadBuiltinFastPath")


    def test_read_global_quickening_global():
        def tester():
            return sys

        for _ in range(5):
            assert tester() is sys
        assert_contains_bytecode(tester, "ReadGlobal$ReadGlobalFastPath")


    def test_get_attr_quickening_module():
        def tester(s):
            return s.modules

        for i in range(5):
            tester(sys)
        assert_contains_bytecode(tester, "GetAttribute$Module")


    def test_get_attr_quickening_module_int():
        def tester_i(s):
            return s.maxsize - 5

        for i in range(5):
            tester_i(sys)

        __graalpython__.tdebug(33)
        tester_i(sys)
        assert_contains_bytecode(tester_i, "GetAttribute$Module$int")


    def test_get_attr_quickening_type():
        class K:
            MY_ATTR = 'forty-two'

        def tester(o):
            return o.MY_ATTR

        for i in range(5):
            assert tester(K) == 'forty-two'
        assert_contains_bytecode(tester, "GetAttribute$Type")


    def test_get_attr_quickening_type_int():
        class Q:
            MY_ATTR = 42

        def tester(o):
            return o.MY_ATTR - 2 + 2

        for i in range(5):
            assert tester(Q) == 42
        assert_contains_bytecode(tester, "GetAttribute$Type$int")


    def test_get_method_str_quickening():
        def tester(s):
            return s.rstrip()

        for i in range(5):
            assert tester('hello ') == 'hello'
        assert_contains_bytecode(tester, "GetMethod$StringFastPath")


    def test_get_method_builtin_quickening():
        def tester(d):
            return d.popitem()

        d = {i:i for i in reversed(range(5))}
        for i in range(5):
            assert tester(d)[0] == i
        assert_contains_bytecode(tester, "GetMethod$FastPath")


    @skipUnlessSingleContext
    def test_call_nilary_method_quickening_python_function():
        def callee():
            return 42

        def tester():
            return callee()

        for _ in range(5):
            assert tester() == 42
        assert_contains_bytecode(tester, "CallNilaryMethod$CallPFunction")


    @skipUnlessSingleContext
    def test_call_unary_method_quickening_python_function():
        def callee(a):
            return a

        def tester():
            return callee(42)

        for _ in range(5):
            assert tester() == 42
        assert_contains_bytecode(tester, "CallUnaryMethod$CallPFunction")


    @skipUnlessSingleContext
    def test_call_binary_method_quickening_python_function():
        def callee(a, b):
            return a + b

        def tester():
            return callee(40, 2)

        for _ in range(5):
            assert tester() == 42
        assert_contains_bytecode(tester, "CallBinaryMethod$CallPFunction")


    @skipUnlessSingleContext
    def test_call_ternary_method_quickening_python_function():
        def callee(a, b, c):
            return a + b + c

        def tester():
            return callee(39, 2, 1)

        for _ in range(5):
            assert tester() == 42
        assert_contains_bytecode(tester, "CallTernaryMethod$CallPFunction")


    @skipUnlessSingleContext
    def test_call_quaternary_method_quickening_python_function():
        def callee(a, b, c, d):
            return a + b + c + d

        def tester():
            return callee(36, 3, 2, 1)

        for _ in range(5):
            assert tester() == 42
        assert_contains_bytecode(tester, "CallQuaternaryMethod$CallPFunction")


    @skipUnlessSingleContext
    def test_call_unary_method_quickening_builtin_method():
        def tester(s):
            return s.rstrip()

        for _ in range(5):
            assert tester("hello ") == "hello"
        assert_contains_bytecode(tester, "CallUnaryMethod$CallObjectSingle")


    @skipUnlessSingleContext
    def test_call_binary_method_quickening_builtin_method():
        def tester(s):
            return s.startswith("hello")

        for _ in range(5):
            assert tester("hello world")
        assert_contains_bytecode(tester, "CallBinaryMethod$CallObjectSingleContext")


    @skipUnlessSingleContext
    def test_call_ternary_method_quickening_builtin_method():
        def tester(s):
            return s.replace("world", "there")

        for _ in range(5):
            assert tester("hello world") == "hello there"
        assert_contains_bytecode(tester, "CallTernaryMethod$BuiltinFunctionCached")


    @skipUnlessSingleContext
    def test_call_quaternary_method_quickening_builtin_method():
        def tester(s):
            return s.replace("l", "x", 1)

        for _ in range(5):
            assert tester("hello") == "hexlo"
        assert_contains_bytecode(tester, "CallQuaternaryMethod$CallSingle")


    @skipUnlessSingleContext
    def test_get_attr_quickening_instance():
        class K:
            def __init__(self):
                self.attr = 42

        def tester(o):
            return o.attr

        for i in range(5):
            assert tester(K()) == 42
        assert_contains_bytecode(tester, "GetAttribute$InstanceValue")


    @skipUnlessSingleContext
    def test_get_attr_quickening_instance_int():
        class K2:
            def __init__(self):
                self.attr2 = 37

        def tester(o):
            return o.attr2 + 5

        for i in range(5):
            assert tester(K2()) == 42
        assert_contains_bytecode(tester, "GetAttribute$InstanceValue$int")


    @skipUnlessSingleContext
    def test_set_attr_quickening():
        class K:
            def __init__(self):
                self.attr = 1

        def tester(o, i):
            o.attr = i

        o = K()
        for i in range(5):
            tester(o, i)
        assert o.attr == 4
        assert_contains_bytecode(tester, "SetAttribute$InstanceValue")


    @skipUnlessSingleContext
    def test_get_method_python_class_quickening():
        class K:
            def foo(self):
                return 'bar'

        def tester(o):
            return o.foo()

        for i in range(5):
            assert tester(K()) == 'bar'
        assert_contains_bytecode(tester, "GetMethod$FastPath")


    @skipUnlessSingleContext
    def test_get_method_builtin_and_pyclass_quickening():
        class MyDict:
            def popitem(self):
                return 42

        def tester(d):
            return d.popitem()

        d = {i:i for i in reversed(range(5))}
        for i in range(5):
            assert tester(d)[0] == i
            assert tester(MyDict()) == 42

        assert_contains_bytecode(tester, "GetMethod$FastPath")


if __name__ == '__main__':
    unittest.main()
