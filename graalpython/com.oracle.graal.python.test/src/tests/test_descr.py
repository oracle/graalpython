# Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import gc
import warnings


def test_cached_instance_load_after_descriptor_added():
    class C:
        pass

    def read(obj):
        return obj.attr

    obj = C()
    obj.attr = "instance"
    for _ in range(20):
        assert read(obj) == "instance"

    C.attr = property(lambda self: "descriptor")
    assert read(obj) == "descriptor"


def test_cached_instance_int_load_after_inherited_descriptor_added():
    class Base:
        pass

    class C(Base):
        pass

    def read(obj):
        return obj.attr + 1

    obj = C()
    obj.attr = 41
    for _ in range(20):
        assert read(obj) == 42

    Base.attr = property(lambda self: 99)
    assert read(obj) == 100


def test_cached_instance_load_after_getattribute_added():
    class C:
        pass

    def read(obj):
        return obj.attr

    obj = C()
    obj.attr = "instance"
    for _ in range(20):
        assert read(obj) == "instance"

    C.__getattribute__ = lambda self, name: "override"
    assert read(obj) == "override"


def test_cached_instance_store_after_descriptor_replaced():
    class C:
        def attr(self):
            pass

    def write(obj, value):
        obj.attr = value

    obj = C()
    for value in range(20):
        write(obj, value)
    assert obj.attr == 19

    writes = []
    C.attr = property(lambda self: "descriptor", lambda self, value: writes.append(value))
    write(obj, 42)
    assert writes == [42]
    assert obj.__dict__["attr"] == 19


def test_cached_instance_store_after_inherited_setattr_added():
    class Base:
        pass

    class C(Base):
        pass

    def write(obj, value):
        obj.attr = value

    obj = C()
    for value in range(20):
        write(obj, value)
    assert obj.attr == 19

    writes = []
    Base.__setattr__ = lambda self, name, value: writes.append((name, value))
    write(obj, 42)
    assert writes == [("attr", 42)]
    assert obj.__dict__["attr"] == 19


def _change_base_while_warming_attribute_access(new_base, warmup):
    class Base:
        pass

    class C(Base):
        pass

    obj = C()
    obj.attr = 41
    armed = False

    class Meta(type):
        def mro(cls):
            if armed:
                # C's MRO has already changed and invalidated its lookup caches,
                # but its slots still come from Base. Specializing here must not
                # leave cached attribute accesses valid after the slots change.
                warmup(obj)
            return super().mro()

    class Child(C, metaclass=Meta):
        pass

    armed = True
    C.__bases__ = (new_base,)
    return obj


def test_cached_instance_load_during_bases_change():
    class Override:
        def __getattribute__(self, name):
            return 99

    def read(obj):
        return obj.attr

    def warmup(obj):
        for _ in range(20):
            read(obj)

    obj = _change_base_while_warming_attribute_access(Override, warmup)
    assert getattr(obj, "attr") == 99
    assert read(obj) == 99


def test_cached_instance_int_load_during_bases_change():
    class Override:
        def __getattribute__(self, name):
            return 99

    def read(obj):
        return obj.attr + 1

    def warmup(obj):
        for _ in range(20):
            read(obj)

    obj = _change_base_while_warming_attribute_access(Override, warmup)
    assert getattr(obj, "attr") == 99
    assert read(obj) == 100


def test_cached_instance_store_during_bases_change():
    writes = []

    class Override:
        def __setattr__(self, name, value):
            writes.append((name, value))

    def write(obj, value):
        obj.attr = value

    def warmup(obj):
        for value in range(20):
            write(obj, value)

    obj = _change_base_while_warming_attribute_access(Override, warmup)
    writes.clear()
    value_before = obj.attr
    write(obj, 42)
    assert writes == [("attr", 42)]
    assert obj.attr == value_before


def test_instance_store_with_non_string_class_dict_key():
    # Such a namespace requires a dictionary that cannot be probed by the
    # side-effect-free descriptor lookup used when specializing STORE_ATTR.
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", RuntimeWarning)
        C = type("C", (), {1: None})

    def write(obj, value):
        obj.attr = value

    obj = C()
    for value in range(20):
        write(obj, value)
        assert obj.attr == value


def test_evil_getattribute():
    # Variation of a CPython test from test_descr.py
    class EvilGetattribute(object):
        def __getattr__(self, name):
            return "original"
        def __getattribute__(self, name):
            EvilGetattribute.__getattr__ = lambda s,n: n
            for i in range(5):
                gc.collect()
            raise AttributeError(name)

    obj = EvilGetattribute()
    assert getattr(obj, "bar") == "original"
    assert getattr(obj, "bar") == "bar"


def test_overwrite___weakref__():
    class C:
        __weakref__ = 1
    assert C.__weakref__ == 1


def test___hash___in___slots__():
    class ObjWithoutHash:
        def __eq__(self, other):
            return True

    assert ObjWithoutHash.__hash__ is None

    class ObjWithHashSlot:
        __slots__ = ("__hash__",)

        def __eq__(self, other):
            return True

    assert ObjWithHashSlot.__hash__ is not None
    o = ObjWithHashSlot()
    o.__hash__ = lambda: 1
    assert hash(o) == 1


def test_attribute_error_message():
    obj = object()

    try:
        obj.foo
    except AttributeError as e:
        assert e.obj == obj
        assert e.name == "foo"
        assert str(e) == "'object' object has no attribute 'foo'"

    try:
        obj.foo = 1
    except AttributeError as e:
        assert e.obj == obj
        assert e.name == "foo"
        assert str(e) == "'object' object has no attribute 'foo' and no __dict__ for setting new attributes"

    class MyClass:
        pass

    try:
        MyClass.foo
    except AttributeError as e:
        assert e.obj == MyClass
        assert e.name == "foo"
        assert str(e) == "type object 'MyClass' has no attribute 'foo'"
