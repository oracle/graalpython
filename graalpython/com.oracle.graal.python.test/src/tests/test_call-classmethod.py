# Copyright (c) 2018, 2026, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# classmethod calls

from tests.util import needs_capi


class Strength(object):

    def __init__(self, strength, name):
        self.strength = strength
        self.name = name

    @classmethod
    def stronger(cls, s1, s2):
        return s1.strength < s2.strength

    @classmethod
    def weaker(cls, s1, s2):
        return s1.strength > s2.strength


def test_strength():
    s1 = Strength(1, 'cat')
    s2 = Strength(2, 'dog')
    stgr = Strength.stronger
    assert stgr(s1, s2)
    assert Strength.stronger(s1, s2)
    assert not Strength.weaker(s1, s2)
    assert s1.stronger(s1, s2)
    assert not s1.weaker(s1, s2)


def test_classmethod_wraps_descriptor():
    class Descriptor:
        def __get__(self, obj, typ=None):
            return obj, typ

    class C:
        method = classmethod(Descriptor())

    assert C.method == (C, C)
    assert C().method == (C, C)

    method = C.__dict__["method"]
    assert method.__get__(None, C) == (C, C)
    assert method.__get__(C()) == (C, C)


def test_classmethod_wraps_property():
    class C:
        @classmethod
        @property
        def name(cls):
            return cls.__name__

    class D(C):
        pass

    assert C.name == "C"
    assert C().name == "C"
    assert D.name == "D"
    assert D().name == "D"


def test_classmethod_wraps_staticmethod():
    class C:
        method = classmethod(staticmethod(lambda value: ("static", value)))

    assert C.method("arg") == ("static", "arg")
    assert C().method("arg") == ("static", "arg")


def test_classmethod_wraps_classmethod():
    class C:
        def method(cls, value):
            return cls, value

        method = classmethod(classmethod(method))

    class D(C):
        pass

    assert C.method("arg") == (C, "arg")
    assert C().method("arg") == (C, "arg")
    assert D.method("arg") == (D, "arg")
    assert D().method("arg") == (D, "arg")


def test_classmethod_wraps_bound_method():
    class C:
        def method(self, cls):
            return self, cls

    receiver = C()
    assert not hasattr(type(receiver.method), "__get__")

    class D:
        method = classmethod(receiver.method)

    assert D.method() == (receiver, D)
    assert D().method() == (receiver, D)


def test_classmethod_descriptor_get_errors():
    descriptor = dict.__dict__["fromkeys"]

    assert descriptor.__get__(None, dict)([1, 2]) == {1: None, 2: None}
    assert descriptor.__get__({})([1, 2]) == {1: None, 2: None}

    for args in ((None, None), (42,), (None, 42), (None, int), ({}, int)):
        try:
            descriptor.__get__(*args)
        except TypeError:
            pass
        else:
            raise AssertionError("classmethod_descriptor.__get__ accepted invalid arguments")


def test_classmethod_descriptor_get_uses_object_type_when_type_omitted():
    class MyDict(dict):
        pass

    descriptor = dict.__dict__["fromkeys"]
    bound = descriptor.__get__(MyDict())
    assert bound.__self__ is MyDict
    assert type(bound([1, 2])) is MyDict


def test_classmethod_descriptor_get_does_not_keep_type_alive():
    import time
    from test import support

    descriptor = object.__dict__["__init_subclass__"]

    class Parent:
        pass

    class Child(Parent):
        pass

    bound = descriptor.__get__(None, Child)
    del bound
    assert Parent.__subclasses__() == [Child]

    del Child
    for _ in range(100):
        support.gc_collect()
        if not Parent.__subclasses__():
            break
        if getattr(support, "is_graalpy", False):
            time.sleep(0.1)
    assert Parent.__subclasses__() == []


@needs_capi
def test_cext_classmethod_descriptor():
    from _ctypes import _SimpleCData

    class c_void_p(_SimpleCData):
        _type_ = "P"

    descriptor = c_void_p.__dict__["from_param"]
    assert type(descriptor).__name__ == "classmethod_descriptor"
    assert callable(descriptor.__get__(None, c_void_p))
    c_void_p.from_param(0)
