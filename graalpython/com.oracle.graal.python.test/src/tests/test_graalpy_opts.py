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
import types
import unittest
import builtins

if sys.implementation.name == "graalpy" and not __graalpython__.is_forced_uncached_interpreter:

    skipUnlessSingleContext = unittest.skipUnless(__graalpython__.is_single_context, "requires single-context mode")
    skipUnlessMultiContext = unittest.skipIf(__graalpython__.is_single_context, "requires multi-context mode")

    def assert_contains_bytecode(fun, bytecode_str):
        bytecode = __graalpython__.dis(fun)
        assert bytecode_str + ' ' in bytecode, bytecode


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
        if __graalpython__.is_single_context:
            assert_contains_bytecode(tester, "ReadGlobal$ReadBuiltinFastPath")
        else:
            assert_contains_bytecode(tester, "ReadGlobal$ReadBuiltinFromStorage")


    def test_read_global_quickening_global():
        def tester():
            return sys

        for _ in range(5):
            assert tester() is sys
        if __graalpython__.is_single_context:
            assert_contains_bytecode(tester, "ReadGlobal$ReadGlobalFastPath")
        else:
            assert_contains_bytecode(tester, "ReadGlobal$ReadGlobalFromStorage")


    def test_get_attr_quickening_module():
        def tester(s):
            return s.modules

        for i in range(5):
            tester(sys)
        assert_contains_bytecode(tester, "GetAttribute$Module")


    def test_get_attr_quickening_module_with_getattr():
        module = types.ModuleType("test_module")
        module.value = "original"
        module.__getattr__ = lambda name: "fallback: " + name

        def tester(mod):
            return mod.value

        for _ in range(5):
            assert tester(module) == "original"
        assert_contains_bytecode(tester, "GetAttribute$Module")

        del module.value
        assert tester(module) == "fallback: value"


    @skipUnlessSingleContext
    def test_get_attr_quickening_module_int():
        def tester_i(s):
            return s.maxsize - 5

        for i in range(5):
            tester_i(sys)

        __graalpython__.tdebug(33)
        tester_i(sys)
        assert_contains_bytecode(tester_i, "GetAttribute$Module$int")


    @skipUnlessSingleContext
    def test_get_attr_quickening_type():
        class K:
            MY_ATTR = 'forty-two'

        def tester(o):
            return o.MY_ATTR

        for i in range(5):
            assert tester(K) == 'forty-two'
        assert_contains_bytecode(tester, "GetAttribute$Type")


    @skipUnlessMultiContext
    def test_get_attr_quickening_builtin_type_multi_context():
        import _blake2

        def tester(o):
            return o.MAX_DIGEST_SIZE

        expected = _blake2.blake2b.MAX_DIGEST_SIZE
        for _ in range(5):
            assert tester(_blake2.blake2b) == expected
        assert_contains_bytecode(tester, "GetAttribute$BuiltinTypeMultiContext")


    @skipUnlessSingleContext
    def test_get_attr_quickening_type_int():
        class Q:
            MY_ATTR = 42

        def tester(o):
            return o.MY_ATTR - 2 + 2

        for i in range(5):
            assert tester(Q) == 42
        assert_contains_bytecode(tester, "GetAttribute$Type$int")


    @skipUnlessSingleContext
    def test_get_attr_quickening_type_mutable_non_descriptor():
        class Value:
            pass

        value = Value()

        class Q:
            MY_ATTR = value

        def tester(o):
            return o.MY_ATTR

        for i in range(5):
            assert tester(Q) is value
        assert_contains_bytecode(tester, "GetAttribute$TypeMutableNonDescriptor")


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
        assert_contains_bytecode(tester, "GetMethod$FastPathBuiltin")


    def test_get_method_module_quickening():
        module = types.ModuleType("test_module")
        module.func = lambda: "instance"

        def tester(mod):
            return mod.func()

        for _ in range(5):
            assert tester(module) == "instance"
        assert_contains_bytecode(tester, "GetMethod$ModuleFastPath")

        del module.func
        module.__getattr__ = lambda name: lambda: "fallback: " + name
        assert tester(module) == "fallback: func"


    def test_get_method_module_subclass_not_quickened():
        class ModuleSubclass(types.ModuleType):
            def __getattribute__(self, name):
                if name == "func":
                    return lambda: "override"
                return super().__getattribute__(name)

        module = ModuleSubclass("test_module_subclass")
        module.func = lambda: "instance"

        def tester(mod):
            return mod.func()

        for _ in range(5):
            assert tester(module) == "override"


    def test_get_method_instance_not_quickened():
        class K:
            func = 42

        obj = K()
        obj.func = lambda: "instance"

        def tester(o):
            return o.func()

        for _ in range(5):
            assert tester(obj) == "instance"

        del obj.func
        assert obj.func == 42


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
    def test_get_attr_quickening_property():
        class K:
            @property
            def attr(self):
                return 42

        def tester(o):
            return o.attr

        for _ in range(5):
            assert tester(K()) == 42
        assert_contains_bytecode(tester, "GetAttribute$Property")
        del K.attr
        instance = K()
        instance.attr = 43
        assert tester(instance) == 43


    @skipUnlessSingleContext
    def test_get_attr_quickening_property_with_setter():
        class K:
            def __init__(self):
                self._attr = 0

            @property
            def attr(self):
                return self._attr

            @attr.setter
            def attr(self, value):
                self._attr = value

        def tester(o):
            return o.attr

        instance = K()
        for i in range(5):
            instance.attr = i
            assert tester(instance) == i
        assert_contains_bytecode(tester, "GetAttribute$Property")

        for i in range(5):
            instance.attr = -i
            assert tester(instance) == -i
        assert_contains_bytecode(tester, "GetAttribute$Property")


    @skipUnlessSingleContext
    def test_get_attr_quickening_indexed_slot():
        class K:
            __slots__ = ("attr",)

        def tester(o):
            return o.attr

        instance = K()
        for i in range(5):
            instance.attr = i
            assert tester(instance) == i
        assert_contains_bytecode(tester, "GetAttribute$IndexedSlotDescriptor")
        K.attr = property(lambda self: "overridden")
        assert tester(instance) == "overridden"


    @skipUnlessSingleContext
    def test_get_attr_quickening_indexed_slot_int():
        class K:
            __slots__ = ("attr",)

        def tester(o):
            return o.attr + 5

        instance = K()
        for i in range(5):
            instance.attr = i
            assert tester(instance) == i + 5
        assert_contains_bytecode(tester, "GetAttribute$IndexedSlotDescriptor$int")

        instance.attr = 1.5
        assert tester(instance) == 6.5
        assert_contains_bytecode(tester, "GetAttribute$IndexedSlotDescriptor$Generic")


    @skipUnlessSingleContext
    def test_get_attr_quickening_indexed_slot_int_invalidation():
        class K:
            __slots__ = ("attr",)

        def tester(o):
            return o.attr + 5

        instance = K()
        instance.attr = 37
        for _ in range(5):
            assert tester(instance) == 42
        assert_contains_bytecode(tester, "GetAttribute$IndexedSlotDescriptor$int")
        K.attr = property(lambda self: 38)
        for _ in range(5):
            assert tester(instance) == 43


    @skipUnlessSingleContext
    def test_get_attr_quickening_inherited_indexed_slot():
        class Base:
            __slots__ = ("attr",)

        class Child(Base):
            pass

        def tester(o):
            return o.attr

        instance = Child()
        instance.attr = 42
        instance.__dict__["attr"] = "shadow"
        for _ in range(5):
            assert tester(instance) == 42
        assert_contains_bytecode(tester, "GetAttribute$IndexedSlotDescriptor")
        del instance.attr
        with unittest.TestCase().assertRaises(AttributeError):
            tester(instance)
        instance.attr = 43
        assert tester(instance) == 43


    @skipUnlessSingleContext
    def test_get_attr_quickening_property_reinitialized():
        class K:
            attr = property(lambda self: 42)

        def tester(o):
            return o.attr

        instance = K()
        for _ in range(5):
            assert tester(instance) == 42
        assert_contains_bytecode(tester, "GetAttribute$Property")
        K.attr.__init__(lambda self: 43)
        for _ in range(5):
            assert tester(instance) == 43
        assert_contains_bytecode(tester, "GetAttribute$Property")
        K.attr = property()
        with unittest.TestCase().assertRaises(AttributeError):
            tester(instance)


    @skipUnlessSingleContext
    def test_get_attr_quickening_inherited_property():
        class Base:
            attr = property(lambda self: 42)

        class Child(Base):
            pass

        def tester(o):
            return o.attr

        instance = Child()
        instance.__dict__["attr"] = "shadow"
        for _ in range(5):
            assert tester(instance) == 42
        assert_contains_bytecode(tester, "GetAttribute$Property")
        Base.attr = property(lambda self: 43)
        assert tester(instance) == 43
        Child.__getattribute__ = lambda self, name: 44
        assert tester(instance) == 44


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
    def test_get_method_builtin_and_pyclass():
        class MyDict:
            def popitem(self):
                return 42

        def tester(d):
            return d.popitem()

        d = {i:i for i in reversed(range(5))}
        for i in range(5):
            assert tester(d)[0] == i
            assert tester(MyDict()) == 42


    @skipUnlessSingleContext
    def test_get_attr_cached_object_or_module_class_value():
        class Base:
            attr = []

        class Child(Base):
            pass

        def tester(obj):
            return obj.attr

        obj = Child()
        for _ in range(5):
            assert tester(obj) is Base.attr
        assert_contains_bytecode(tester, "GetAttribute$CachedObjectOrModule")
        Base.attr = "changed"
        assert tester(obj) == "changed"
        obj.attr = "instance"
        assert tester(obj) == "instance"
        del obj.attr
        assert tester(obj) == "changed"
        Child.__getattr__ = lambda self, name: "fallback"
        del Base.attr
        assert tester(obj) == "fallback"


    @skipUnlessSingleContext
    def test_get_attr_cached_object_or_module_descriptor_slots():
        class Descriptor:
            def __get__(self, instance, owner):
                return instance, owner

        descriptor = Descriptor()

        class K:
            attr = descriptor

        def tester(obj):
            return obj.attr

        obj = K()
        for _ in range(5):
            assert tester(obj) == (obj, K)
        assert_contains_bytecode(tester, "GetAttribute$CachedObjectOrModule")
        obj.attr = "instance"
        assert tester(obj) == "instance"
        Descriptor.__set__ = lambda self, instance, value: None
        assert tester(obj) == (obj, K)
        del Descriptor.__get__
        assert tester(obj) == "instance"
        del Descriptor.__set__
        del obj.attr
        assert tester(obj) is descriptor
        Descriptor.__get__ = lambda self, instance, owner: "new get"
        assert tester(obj) == "new get"


    @skipUnlessSingleContext
    def test_get_attr_quickening_instance_five_shapes():
        class K:
            pass

        def tester(obj):
            return obj.attr

        def tester_int(obj):
            return obj.attr + 1

        objects = []
        for i in range(5):
            obj = K()
            setattr(obj, "other" + str(i), None)
            obj.attr = str(i)
            objects.append(obj)
        for _ in range(5):
            for i, obj in enumerate(objects):
                assert tester(obj) == str(i)
        assert_contains_bytecode(tester, "GetAttribute$InstanceValue")
        class IntK:
            pass

        int_objects = []
        for i in range(5):
            obj = IntK()
            setattr(obj, "other" + str(i), None)
            obj.attr = i
            int_objects.append(obj)
        for _ in range(5):
            for i, obj in enumerate(int_objects):
                assert tester_int(obj) == i + 1
        assert_contains_bytecode(tester_int, "GetAttribute$InstanceValue$int")


    @skipUnlessSingleContext
    def test_get_attr_cached_object_or_module_five_shapes():
        class K:
            attr = []

        def tester(obj):
            return obj.attr

        objects = []
        for i in range(5):
            obj = K()
            setattr(obj, "other" + str(i), None)
            objects.append(obj)
        for _ in range(5):
            for obj in objects:
                assert tester(obj) is K.attr
        assert_contains_bytecode(tester, "GetAttribute$CachedObjectOrModule")
        objects[0].attr = "instance"
        assert tester(objects[0]) == "instance"


    @skipUnlessSingleContext
    def test_get_attr_generic_beyond_fast_cache():
        class K:
            pass

        def tester(obj):
            return obj.attr

        objects = []
        # Six shapes overflow both five-entry caches.
        for i in range(6):
            obj = K()
            setattr(obj, "other" + str(i), None)
            obj.attr = str(i)
            objects.append(obj)
        for _ in range(5):
            for i, obj in enumerate(objects):
                assert tester(obj) == str(i)
        assert_contains_bytecode(tester, "GetAttribute$It")
        objects[0].__dict__ = {"attr": "dictionary"}
        assert tester(objects[0]) == "dictionary"
        del objects[1].attr
        with unittest.TestCase().assertRaises(AttributeError):
            tester(objects[1])


    @skipUnlessSingleContext
    def test_get_attr_cached_object_or_module_mixed_descriptors():
        class Plain:
            attr = []

        class Property:
            attr = property(lambda self: "property")

        class Slot:
            __slots__ = ("attr",)

        def tester(obj):
            return obj.attr

        slot = Slot()
        slot.attr = "slot"
        objects = (Plain(), Property(), slot)
        for _ in range(5):
            for obj, expected in zip(objects, (Plain.attr, "property", "slot")):
                assert tester(obj) == expected
        assert_contains_bytecode(tester, "GetAttribute$CachedObjectOrModule")
        Property.attr = property(lambda self: "changed")
        assert tester(objects[1]) == "changed"
        del slot.attr
        with unittest.TestCase().assertRaises(AttributeError):
            tester(slot)


    @skipUnlessSingleContext
    def test_get_attr_cached_object_or_module_module_descriptor_error():
        class Descriptor:
            def __get__(self, instance, owner):
                raise AttributeError("descriptor failure")

        class Module(types.ModuleType):
            attr = Descriptor()

        module = Module("cached_module")
        module.__getattr__ = lambda name: "module fallback: " + name

        def tester(obj):
            return obj.attr

        for _ in range(5):
            assert tester(module) == "module fallback: attr"
        assert_contains_bytecode(tester, "GetAttribute$CachedObjectOrModule")
        del module.__getattr__
        with unittest.TestCase().assertRaisesRegex(AttributeError, "attr"):
            tester(module)


    def test_get_attr_quickening_getset_descriptor():
        def tester(obj):
            return obj.real

        for _ in range(5):
            assert tester(1 + 2j) == 1.0
        if __graalpython__.is_single_context:
            assert_contains_bytecode(tester, "GetAttribute$GetSetDescriptor")
        else:
            assert_contains_bytecode(tester, "GetAttribute$It")


    @skipUnlessSingleContext
    def test_get_attr_generic_rejects_custom_getattribute():
        class K:
            attr = "class"

            def __getattribute__(self, name):
                return "custom"

        def tester(obj):
            return obj.attr

        for _ in range(5):
            assert tester(K()) == "custom"
        assert_contains_bytecode(tester, "GetAttribute$It")


    @skipUnlessSingleContext
    def test_get_attr_quickening_getset_descriptor_inherited():
        class K(complex):
            attr = complex.real

        def tester(obj):
            return obj.attr

        obj = K(1, 2)
        obj.__dict__["attr"] = "shadow"
        for _ in range(5):
            assert tester(obj) == 1.0
        assert_contains_bytecode(tester, "GetAttribute$GetSetDescriptor")
        K.attr = property(lambda self: "changed")
        assert tester(obj) == "changed"


    @skipUnlessSingleContext
    def test_get_attr_quickening_getset_descriptor_owner_check():
        class K:
            attr = complex.real

        def tester(obj):
            return obj.attr

        for _ in range(5):
            with unittest.TestCase().assertRaises(TypeError):
                tester(K())
        assert_contains_bytecode(tester, "GetAttribute$CachedObjectOrModule")


    @skipUnlessSingleContext
    def test_get_attr_quickening_getset_descriptor_custom_getattribute():
        class K(complex):
            def __getattribute__(self, name):
                return "custom"

        def tester(obj):
            return obj.real

        for _ in range(5):
            assert tester(K(1, 2)) == "custom"
        assert_contains_bytecode(tester, "GetAttribute$It")


    @skipUnlessSingleContext
    def test_get_attr_quickening_tuple_getter():
        from collections import namedtuple
        Pair = namedtuple("Pair", "first second")

        class K(Pair):
            pass

        def tester(obj):
            return obj.first

        obj = K("first", "second")
        obj.__dict__["first"] = "shadow"
        for _ in range(5):
            assert tester(obj) == "first"
        assert_contains_bytecode(tester, "GetAttribute$TupleGetter")
        K.first = property(lambda self: "changed")
        assert tester(obj) == "changed"


    @skipUnlessSingleContext
    def test_get_attr_quickening_tuple_getter_bounds_and_len():
        from _collections import _tuplegetter

        class K(tuple):
            attr = _tuplegetter(1, "doc")

        def tester(obj):
            return obj.attr

        for _ in range(5):
            assert tester(K((1, 2))) == 2
        assert_contains_bytecode(tester, "GetAttribute$TupleGetter")
        with unittest.TestCase().assertRaises(IndexError):
            tester(K((1,)))
        K.__len__ = lambda self: 1
        assert tester(K((1, 2))) == 2


    @skipUnlessSingleContext
    def test_get_attr_quickening_tuple_getter_negative_index():
        from _collections import _tuplegetter

        class K(tuple):
            attr = _tuplegetter(-1, "doc")

            def __getitem__(self, index):
                raise AssertionError("tuple getter must bypass __getitem__")

        def tester(obj):
            return obj.attr

        for _ in range(5):
            with unittest.TestCase().assertRaises(IndexError):
                tester(K((1, 2)))
        assert_contains_bytecode(tester, "GetAttribute$TupleGetter")
        with unittest.TestCase().assertRaises(IndexError):
            tester(K(()))


    @skipUnlessSingleContext
    def test_get_attr_quickening_tuple_getter_overridden_len():
        from _collections import _tuplegetter

        class K(tuple):
            attr = _tuplegetter(1, "doc")

            def __len__(self):
                return 1

        def tester(obj):
            return obj.attr

        obj = K((1, 2))
        for _ in range(5):
            assert tester(obj) == 2
        assert_contains_bytecode(tester, "GetAttribute$TupleGetter")


    @skipUnlessSingleContext
    def test_get_attr_quickening_tuple_getter_does_not_call_len_or_getitem():
        from collections import namedtuple
        Pair = namedtuple("Pair", "first second")

        class K(Pair):
            def __len__(self):
                raise AssertionError("tuple getter must bypass __len__")

            def __getitem__(self, index):
                raise AssertionError("tuple getter must bypass __getitem__")

        def tester(obj):
            return obj.second

        obj = K("first", "second")
        for _ in range(5):
            assert tester(obj) == "second"
        assert_contains_bytecode(tester, "GetAttribute$TupleGetter")


if __name__ == '__main__':
    unittest.main()
