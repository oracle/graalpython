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


def test_super():
    class A:
        def m(self): return ["A"]
    class B(A):
        def m(self): return ["B"] + self.my_super.m()
    B.my_super = super(B)
    B.my_super = B.my_super

    assert B().m() == ["B", "A"]


def test_super_requires_type_arg():
    try:
        super([])
    except TypeError:
        pass
    else:
        assert False


def test_super_subclass_descr_get_invokes_subclass_type():
    class MySuper(super):
        news = []
        calls = []

        def __new__(cls, *args):
            cls.news.append(args)
            return super().__new__(cls)

        def __init__(self, *args):
            type(self).calls.append(args)
            super().__init__(*args)

    class A:
        def f(self):
            return "A.f"

    class B(A):
        pass

    raw = MySuper(B)
    MySuper.news.clear()
    MySuper.calls.clear()
    obj = B()
    bound = raw.__get__(obj, B)

    assert type(bound) is MySuper
    assert MySuper.news == [(B, obj)]
    assert MySuper.calls == [(B, obj)]
    assert bound.f() == "A.f"

    raw = MySuper.__new__(MySuper)
    MySuper.news.clear()
    MySuper.calls.clear()
    bound = raw.__get__(obj, B)

    assert type(bound) is MySuper
    assert MySuper.news == [()]
    assert MySuper.calls == [()]


def test_super_lookup_mro_change_during_dict_key_equality():
    calls = []

    class Key(str):
        def __hash__(self):
            return hash("missing")

        def __eq__(self, other):
            calls.append(other)
            Derived.__bases__ = (Replacement,)
            return False

    Base = type("Base", (), {Key("collision"): 42})

    class Replacement:
        missing = 42

    class Derived(Base):
        def read(self):
            return super().missing

    try:
        Derived().read()
    except AttributeError:
        pass
    else:
        assert False
    assert calls == ["missing"]
    assert Derived().read() == 42


def test_super_lookup_suffix_mro_change_during_dict_key_equality():
    calls = []

    class Key(str):
        def __hash__(self):
            return hash("value")

        def __eq__(self, other):
            calls.append(other)
            Base.__bases__ = (Replacement,)
            return False

    class Original:
        value = "original"

    class Replacement:
        value = "replacement"

    Base = type("Base", (Original,), {Key("collision"): 42})

    class Derived(Base):
        def read(self):
            return super().value

    obj = Derived()
    assert obj.read() == "original"
    assert calls == ["value"]
    assert obj.read() == "replacement"


def test_super_lookup_shadowed_attribute_invalidation():
    class A:
        value = "A"

    class B(A):
        value = "B"

    class C(B):
        value = "C"

        def read(self):
            return super().value

    obj = C()
    for _ in range(10):
        assert obj.value == "C"
        assert obj.read() == "B"

    C.value = "new C"
    assert obj.read() == "B"
    A.value = "new A"
    assert obj.read() == "B"
    B.value = "new B"
    assert obj.read() == "new B"
    del B.value
    assert obj.read() == "new A"


def test_super_lookup_missing_attribute_invalidation():
    class A:
        pass

    class B(A):
        pass

    class C(B):
        def read(self):
            return super().value

    obj = C()
    for _ in range(10):
        try:
            obj.read()
        except AttributeError:
            pass
        else:
            assert False

    C.value = "C"
    try:
        obj.read()
    except AttributeError:
        pass
    else:
        assert False
    A.value = "A"
    assert obj.read() == "A"
    B.value = "B"
    assert obj.read() == "B"
    del B.value
    assert obj.read() == "A"


def test_super_lookup_different_start_types_invalidation():
    class A:
        value = "A"

    class B(A):
        value = "B"

    class C(B):
        value = "C"

    obj = C()

    def read(start):
        return super(start, obj).value

    for _ in range(10):
        assert read(C) == "B"
        assert read(B) == "A"

    C.value = "new C"
    assert read(C) == "B"
    assert read(B) == "A"
    B.value = "new B"
    assert read(C) == "new B"
    assert read(B) == "A"
    A.value = "new A"
    assert read(C) == "new B"
    assert read(B) == "new A"
    del B.value
    assert read(C) == "new A"


def test_super_lookup_shares_suffix_lookup():
    class A:
        value = "A"

    class B(A):
        value = "B"

    class C(B):
        value = "C"

    obj = C()

    def read():
        return super(C, obj).value

    for _ in range(10):
        assert read() == "B"
        assert B.value == "B"
        assert C.value == "C"
    del B.value
    assert read() == "A"
    assert B.value == "A"
    assert C.value == "C"


def test_super_lookup_receiver_mro_change():
    class A:
        value = "A"

    class B:
        value = "B"

    class C(A):
        def read(self):
            return super().value

    obj = C()
    for _ in range(10):
        assert obj.read() == "A"
    C.__bases__ = (B,)
    assert obj.read() == "B"
    assert A.value == "A"


def test_super_lookup_diamond_suffix():
    class A:
        value = "A"

    class B(A):
        pass

    class C(A):
        value = "C"

    class D(B, C):
        def read(self):
            return super().value

    obj = D()
    for _ in range(10):
        assert B.value == "A"
        assert obj.read() == "C"
    C.value = "new C"
    assert obj.read() == "new C"
    del C.value
    assert obj.read() == "A"


def test_super_lookup_reordered_suffix():
    class A:
        value = "A"

    class B:
        value = "B"

    class C(A, B):
        pass

    class Meta(type):
        def mro(cls):
            return [cls, C, B, A, object]

    class D(C, metaclass=Meta):
        def read(self):
            return super().value

    obj = D()
    for _ in range(10):
        assert C.value == "A"
        assert obj.read() == "B"
