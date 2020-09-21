# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
from unittest import skipIf


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_base():
    A = type('A', (), {})
    assert A.__base__ == object

    class B:
        def ham(self):
            return 'ham%d' % self

    class A(object):
        pass

    class B(dict):
        pass

    class C(A, B):
        pass

    assert C.__base__ == B

    #-----------------------------------------------

    class A(object):
        pass

    class B(object):
        pass

    class C(A, B):
        pass

    assert C.__base__ == A

    C = type('C', (B, int), {'spam': lambda self: 'spam%s' % self})
    assert C.__base__ == int

    #-----------------------------------------------

    class A (object): pass

    class BB (A): pass

    class B(BB): pass

    class C (A): 
        __slots__ = ['a']

    class D (B, C): pass

    assert D.__base__ == C

    #-----------------------------------------------

    class A: pass

    class B: pass

    class C(A): pass

    C.__bases__ = (A, B)

    assert C.__bases__ == (A, B)
    assert A.__subclasses__() == [C]
    assert B.__subclasses__() == [C]

    #-----------------------------------------------

    class A: pass

    class B: pass

    class C(A, B): pass

    C.__bases__ == (A, B)
    A.__subclasses__() == [C]
    B.__subclasses__() == [C]

    raised = False
    try:
        C.__bases__ = (int,)
    except TypeError:
        raised = True
    assert raised

    assert C.__bases__ == (A, B)
    assert A.__subclasses__() == [C]
    assert B.__subclasses__() == [C]    

    #-----------------------------------------------

#    class A: pass
#
#    class B: pass
#
#    class C: pass
#
#    raised = False
#    try:
#        C.__bases__ = (A, B)
#    except TypeError:
#        raised = True
#    assert raised
#    assert C.__bases__ == [object]

def test_namespace_with_non_string_keys():
    class MyStr(str):
        pass

    A = type('A', (), {
        MyStr("x"): 42
    })
    assert any(type(k) == MyStr for k in A.__dict__.keys())

def test_mro():
    class M(type):
        def mro(cls):
            assert type.mro(cls) == [cls, A, B, object]
            return [cls, B, A, object]

    class A: pass
    class B: pass
    class C(A, B, metaclass = M): pass

    assert C.__mro__ == (C, B, A, object)
    
def test_dir_sorted():
    class C:
        b = 1
        a = 2

    assert dir(C) == sorted(dir(C))
    assert dir(C()) == sorted(dir(C()))


def test_isinstance_non_type():
    import typing
    assert isinstance(1, typing.AbstractSet) is False


@skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 8), "skipping for cPython versions < 3.8")
def test_flags():
    import functools

    def testfunction(self):
        """some doc"""
        return self

    TPFLAGS_METHOD_DESCRIPTOR = 1 << 17
    TPFLAGS_LONG_SUBCLASS = 1 << 24
    TPFLAGS_LIST_SUBCLASS = 1 << 25
    TPFLAGS_TUPLE_SUBCLASS = 1 << 26
    TPFLAGS_BYTES_SUBCLASS = 1 << 27
    TPFLAGS_UNICODE_SUBCLASS = 1 << 28
    TPFLAGS_DICT_SUBCLASS = 1 << 29
    TPFLAGS_BASE_EXC_SUBCLASS = 1 << 30
    TPFLAGS_TYPE_SUBCLASS = 1 << 31

    cached = functools.lru_cache(1)(testfunction)

    assert not type(repr).__flags__ & TPFLAGS_METHOD_DESCRIPTOR, "masked __flags__ = {}, expected {}".format(type(repr).__flags__ & TPFLAGS_METHOD_DESCRIPTOR, 0)
    assert type(list.append).__flags__ & TPFLAGS_METHOD_DESCRIPTOR, "masked __flags__ = {}, expected {}".format(type(repr).__flags__ & TPFLAGS_METHOD_DESCRIPTOR, TPFLAGS_METHOD_DESCRIPTOR)
    assert type(list.__add__).__flags__ & TPFLAGS_METHOD_DESCRIPTOR, "masked __flags__ = {}, expected {}".format(type(repr).__flags__ & TPFLAGS_METHOD_DESCRIPTOR, TPFLAGS_METHOD_DESCRIPTOR)
    assert type(testfunction).__flags__ & TPFLAGS_METHOD_DESCRIPTOR, "masked __flags__ = {}, expected {}".format(type(repr).__flags__ & TPFLAGS_METHOD_DESCRIPTOR, TPFLAGS_METHOD_DESCRIPTOR)
    assert type(cached).__flags__ & TPFLAGS_METHOD_DESCRIPTOR, "masked __flags__ = {}, expected {}".format(type(repr).__flags__ & TPFLAGS_METHOD_DESCRIPTOR, TPFLAGS_METHOD_DESCRIPTOR)

    class MyInt(int):
        pass

    class MyList(list):
        pass

    class MyTuple(tuple):
        pass

    class MyBytes(bytes):
        pass

    class MyStr(str):
        pass

    class MyDict(dict):
        pass

    for x, flag in [
        (1, TPFLAGS_LONG_SUBCLASS),
        (MyInt(1), TPFLAGS_LONG_SUBCLASS),

        ([1,2], TPFLAGS_LIST_SUBCLASS),
        (MyList([1,2]), TPFLAGS_LIST_SUBCLASS),

        ((1,2), TPFLAGS_TUPLE_SUBCLASS),
        (MyTuple((1,2)), TPFLAGS_TUPLE_SUBCLASS),

        (b"123", TPFLAGS_BYTES_SUBCLASS),
        (MyBytes(b"123"), TPFLAGS_BYTES_SUBCLASS),

        ("123", TPFLAGS_UNICODE_SUBCLASS),
        (MyStr("123"), TPFLAGS_UNICODE_SUBCLASS),

        ({"1":1, "2": 2}, TPFLAGS_DICT_SUBCLASS),
        (MyDict({"1":1, "2": 2}), TPFLAGS_DICT_SUBCLASS),
    ]:
        assert type(x).__flags__ & flag, "masked __flags__ = {}, expected {}".format(type(x).__flags__ & flag, flag)

def test_dict():
    def dict_element_raises(o, err):
        raised = False
        try:
            o['__dict__']
        except err:
            raised = True
        assert raised        
        
    class Base:
        pass
    
    class Sub(Base):
        pass
    
    str(type(Base.__dict__['__dict__'])) == "<class 'get_set_desc'>"
    dict_element_raises(Sub.__dict__, KeyError)    
    Base().__dict__ == {}    
    Sub().__dict__ == {}            

    class BaseSlots:
        __slots__ = ['a']
        
    dict_element_raises(BaseSlots.__dict__, KeyError)        
    raised = False
    try:
        BaseSlots().__dict__
    except AttributeError:
        raised = True
    assert raised
    
    class SubSlots(BaseSlots):
        pass

    str(type(SubSlots.__dict__['__dict__'])) == "<class 'get_set_desc'>"
    assert SubSlots().__dict__ == {}
    
    class SubSlots(BaseSlots, Base):
        pass
    
    str(type(SubSlots.__dict__['__dict__'])) == "<class 'get_set_desc'>"
    assert SubSlots().__dict__ == {}
        
def test_itemsize():
    assert object.__itemsize__ == 0
    assert list.__itemsize__ == 0
    assert type.__itemsize__ == 40
    assert tuple.__itemsize__ == 8
    
    class C: pass
    assert C.__itemsize__ == 0
    
    class C(tuple): pass
    assert C.__itemsize__ == 8
    
    
    raised = False
    try:
        object.__itemsize__ = 1
    except TypeError:
        raised = True
    assert raised
    
    raised = False
    try:
        C.__itemsize__ = 1
    except AttributeError:
        raised = True
    assert raised

    class C():
        __itemsize__ = 'abc'
    assert C.__itemsize__ == 0
    
    class C(tuple):
        __itemsize__ = 42
    assert C.__itemsize__ == 8