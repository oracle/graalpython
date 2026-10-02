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

import sys
import traceback
import weakref

from tests import util


class A:
    def f(self):
        return "a"


class B(A):
    def f(self):
        return super().f() + "b"


class C(A):
    def f(self):
        yield super().f() + "c"


def test_super_without_arguments():
    assert B().f() == "ab"


def test_super_in_generator():
    assert list(C().f()) == ["ac"]


def test_super_with_none_first_parameter_is_unbound():
    class Base:
        value = 42

    class Derived(Base):
        def direct(self):
            return super().__thisclass__, super().__self__, super().__self_class__

        def saved(self):
            proxy = super()
            return proxy.__thisclass__, proxy.__self__, proxy.__self_class__

        def bind(self, instance):
            return super().__get__(instance, Derived)

        def missing(self):
            return super().value

    assert Derived.saved(None) == (Derived, None, None)
    assert Derived.direct(None) == Derived.saved(None)
    instance = Derived()
    bound = Derived.bind(None, instance)
    assert bound.__thisclass__ is Derived
    assert bound.__self__ is instance
    assert bound.value == 42
    try:
        Derived.missing(None)
    except AttributeError:
        pass
    else:
        assert False


def test_super_uses_current_first_parameter():
    class Base:
        def identity(self):
            return self

    class Derived(Base):
        def reassigned(self, other):
            self = other
            return super().identity()

        def deleted(self):
            del self
            return super().identity()

    first = Derived()
    second = Derived()
    assert first.reassigned(second) is second
    try:
        first.deleted()
    except (RuntimeError, UnboundLocalError) as caught:
        assert "deleted" in str(caught) or "self" in str(caught)
    else:
        assert False


def test_super_in_nested_function_uses_nested_first_parameter():
    class Base:
        def identity(self):
            return self

    class Derived(Base):
        def make(self):
            def nested(other):
                return super().identity()
            return nested

    first = Derived()
    second = Derived()
    assert first.make()(second) is second


def test_replacement_precedes_implicit_context_errors():
    class Base:
        value = "base"

    class Derived(Base):
        def read_after_delete(self):
            del self
            return super().value

    namespace = globals()
    previous = namespace.get("super")
    had_previous = "super" in namespace
    replacement = type("Replacement", (), {"value": "replacement"})()
    try:
        namespace["super"] = lambda: replacement
        if sys.implementation.name == "graalpy":
            assert Derived().read_after_delete() == "replacement"
        else:
            try:
                Derived().read_after_delete()
            except UnboundLocalError:
                pass
            else:
                assert False
    finally:
        if had_previous:
            namespace["super"] = previous
        else:
            namespace.pop("super", None)


def test_generator_keeps_selected_super_method_across_yield():
    class Base:
        def method(self, value):
            return "old", self, value

    class Derived(Base):
        def run(self):
            return super().method((yield "ready"))

    obj = Derived()
    generator = obj.run()
    assert next(generator) == "ready"
    Base.method = lambda self, value: ("new", self, value)
    try:
        generator.send(42)
    except StopIteration as stopped:
        assert stopped.value == ("old", obj, 42)
    else:
        assert False


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


def test_direct_super_invalid_type_with_none():
    def attribute(cls):
        return super(cls, None).__self__

    def method(cls):
        return super(cls, None).__repr__()

    for operation in (attribute, method):
        operation(object)
        try:
            operation(42)
        except TypeError as error:
            assert "type" in str(error)
        else:
            assert False
        operation(object)


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


def test_direct_super_lookup_and_binding():
    descriptor_calls = []

    class Descriptor:
        def __get__(self, instance, owner):
            descriptor_calls.append((instance, owner))
            return lambda value: (instance, owner, value)

    class A:
        value = 40
        descriptor = Descriptor()

        def method(self, value):
            return self, value

        @staticmethod
        def static(value):
            return "static", value

        @classmethod
        def classmethod_(cls, value):
            return cls, value

        @property
        def property_(self):
            return self.value + 2

    class B(A):
        def read(self):
            return super().value, super(B, self).property_

        def call(self):
            return super().method(2)

        def call_descriptor(self):
            return super().descriptor(3)

        def call_static(self):
            return super().static(4)

        def call_classmethod(self):
            return super().classmethod_(5)

        @classmethod
        def unbound(cls, obj):
            return super(B, B).method(obj, 6)

    obj = B()
    obj.value = -1
    assert obj.read() == (40, 1)
    assert obj.call() == (obj, 2)
    assert obj.call_descriptor() == (obj, B, 3)
    assert descriptor_calls == [(obj, B)]
    assert obj.call_static() == ("static", 4)
    assert obj.call_classmethod() == (B, 5)
    assert B.unbound(obj) == (obj, 6)


def test_direct_super_observes_mutation_after_warmup():
    class Root:
        pass

    class A(Root):
        value = 1

        def method(self):
            return 1

    class Other(Root):
        value = 10

        def method(self):
            return 10

    class B(A):
        def read(self):
            return super().value

        def call(self):
            return super().method()

    obj = B()
    for _ in range(5):
        assert (obj.read(), obj.call()) == (1, 1)
    A.value = 2
    A.method = lambda self: 2
    assert (obj.read(), obj.call()) == (2, 2)
    B.__bases__ = (Other,)
    assert (obj.read(), obj.call()) == (10, 10)


def test_direct_super_descriptor_errors_are_not_missing():
    class Raising:
        def __init__(self, exception):
            self.exception = exception

        def __get__(self, instance, owner):
            raise self.exception("from descriptor")

    class A:
        attr_error = Raising(AttributeError)
        value_error = Raising(ValueError)

    class B(A):
        def attr(self):
            return super().attr_error

        def value(self):
            return super().value_error

    for function, exception in ((B().attr, AttributeError), (B().value, ValueError)):
        try:
            function()
        except exception as caught:
            assert "from descriptor" in str(caught)
        else:
            assert False


def test_direct_super_replacement_and_ordering():
    events = []

    class Result:
        def __getattribute__(self, name):
            events.append(("lookup", name))
            if name == "method":
                return lambda value: events.append(("invoke", value)) or value
            return object.__getattribute__(self, name)

    def replacement(type_arg, object_arg):
        events.append(("replacement", type_arg, object_arg))
        return Result()

    class A:
        pass

    class B(A):
        def run(self):
            def type_arg():
                events.append("type")
                return B

            def object_arg():
                events.append("object")
                return self

            def outer_arg():
                events.append("outer")
                return 42

            return super(type_arg(), object_arg()).method(outer_arg())

    namespace = globals()
    previous = namespace.get("super")
    had_previous = "super" in namespace
    try:
        namespace["super"] = replacement
        obj = B()
        assert obj.run() == 42
        assert events == ["type", "object", ("replacement", B, obj), ("lookup", "method"), "outer", ("invoke", 42)]
    finally:
        if had_previous:
            namespace["super"] = previous
        else:
            namespace.pop("super", None)


def test_direct_super_replacement_profile_events():
    events = []

    class User:
        def get(self, key):
            return super().get(key)

        def missing(self):
            return super().missing

        def fail(self):
            return super().value

    def profile(frame, event, arg):
        if event.startswith("c_") and (arg is globals or arg is len):
            events.append((event, arg))

    namespace = globals()
    previous = namespace.get("super")
    had_previous = "super" in namespace
    key = "__super_profile_events"
    try:
        namespace[key] = events
        namespace["super"] = globals
        for _ in range(5):
            assert User().get(key) is events

        sys.setprofile(profile)
        assert User().get(key) is events
        try:
            User().missing()
        except AttributeError:
            pass
        else:
            assert False
        namespace["super"] = len
        try:
            User().fail()
        except TypeError:
            pass
        else:
            assert False
        sys.setprofile(None)

        assert events == [
            ("c_call", globals),
            ("c_return", globals),
            ("c_call", globals),
            ("c_return", globals),
            ("c_call", len),
            ("c_exception", len),
        ]
    finally:
        sys.setprofile(None)
        namespace.pop(key, None)
        if had_previous:
            namespace["super"] = previous
        else:
            namespace.pop("super", None)


def test_direct_super_profile_nested_calls():
    events = []

    class Base:
        @property
        def value(self):
            return [len(())]

        def number(self):
            return -1

        @property
        def broken(self):
            len(())
            raise ValueError("descriptor")

    class Derived(Base):
        def zero(self):
            return len(super().value), abs(super().number())

        def explicit(self):
            return len(super(Derived, self).value), abs(super(Derived, self).number())

        def failing(self):
            return abs(super().broken)

        def invalid(self):
            return abs(super(42, None).number())

        def nested(self):
            return len(super(type(super().number()), self).value)

    def profile(frame, event, arg):
        if event.startswith("c_") and (arg is len or arg is abs):
            events.append((event, arg))

    obj = Derived()
    for operation in (obj.zero, obj.explicit):
        assert operation() == (1, 1)
        events.clear()
        try:
            sys.setprofile(profile)
            assert operation() == (1, 1)
        finally:
            sys.setprofile(None)
        assert events == [
            ("c_call", len), ("c_return", len),
            ("c_call", len), ("c_return", len),
            ("c_call", abs), ("c_return", abs),
        ]

    for operation, error, expected in (
            (obj.failing, ValueError, [("c_call", len), ("c_return", len)]),
            (obj.invalid, TypeError, []),
            (obj.nested, TypeError, [])):
        events.clear()
        try:
            sys.setprofile(profile)
            try:
                operation()
            except error:
                pass
            else:
                assert False
        finally:
            sys.setprofile(None)
        assert events == expected


def test_direct_super_method_does_not_retain_receiver_after_exception():
    class Marker(Exception):
        pass

    class A:
        dynamic = int.real

        def method(self, value):
            return value

    class B(A):
        @staticmethod
        def outer_argument_case(refs):
            def make_receiver():
                receiver = B()
                refs.append(weakref.ref(receiver))
                return receiver

            def argument(raises):
                if raises:
                    raise Marker("argument")
                return 41

            for raises in (True, False):
                try:
                    result = super(B, make_receiver()).method(argument(raises))
                except Marker as caught:
                    assert raises
                    caught.__traceback__ = None
                    caught = None
                    util.gc_collect(lambda: refs[-1]() is not None)
                    assert refs[-1]() is None
                else:
                    assert not raises
                    assert result == 41

        @staticmethod
        def descriptor_case(refs):
            def make_receiver():
                receiver = B()
                refs.append(weakref.ref(receiver))
                return receiver

            for raises in (True, False):
                A.dynamic = int.real if raises else lambda self: 42
                try:
                    result = super(B, make_receiver()).dynamic()
                except TypeError as caught:
                    assert raises
                    caught.__traceback__ = None
                    caught = None
                    if sys.implementation.name != "graalpy":
                        # TODO: temporary locals remain live until this frame returns
                        # fix with StackValues when we have multi-return in Bytecode DSL
                        util.gc_collect(lambda: refs[-1]() is not None)
                        assert refs[-1]() is None
                else:
                    assert not raises
                    assert result == 42

    refs = []
    B.outer_argument_case(refs)
    B.descriptor_case(refs)
    util.gc_collect(lambda: any(ref() is not None for ref in refs))
    assert all(ref() is None for ref in refs)


def test_direct_super_replacement_releases_constructor_operands():
    class Result:
        def method(self, value):
            return value

    class Replacement:
        def __call__(self, cls, obj):
            return Result()

    refs = []

    def make_type():
        cls = type("Temporary", (), {})
        refs.append(weakref.ref(cls))
        return cls

    def make_receiver():
        obj = Result()
        refs.append(weakref.ref(obj))
        return obj

    def run():
        return super(make_type(), make_receiver()).method((yield "ready"))

    namespace = globals()
    had_previous = "super" in namespace
    previous = namespace.get("super")
    replacement = Replacement()
    refs.append(weakref.ref(replacement))
    namespace["super"] = replacement
    del replacement
    generator = run()
    try:
        assert next(generator) == "ready"
    finally:
        if had_previous:
            namespace["super"] = previous
        else:
            namespace.pop("super", None)
    util.gc_collect(lambda: any(ref() is not None for ref in refs))
    assert all(ref() is None for ref in refs)
    try:
        generator.send(42)
    except StopIteration as stopped:
        assert stopped.value == 42
    else:
        assert False


def test_direct_super_method_lookup_traceback_span():
    for constructor in ("super()", "super(B, self)", "super(42, self)"):
        for attribute in ("missing", "broken"):
            expression = f"{constructor}.{attribute}"
            line = f"        return {expression}(3)"
            source = ("class A:\n"
                      "    @property\n"
                      "    def broken(self):\n"
                      "        raise ValueError('descriptor')\n"
                      "class B(A):\n"
                      "    def run(self):\n" + line + "\n")
            namespace = {}
            exec(compile(source, "<super-span>", "exec"), namespace)
            try:
                namespace["B"]().run()
            except (TypeError, AttributeError, ValueError) as error:
                entry = next(entry for entry in traceback.extract_tb(error.__traceback__)
                             if entry.name == "run")
                assert entry.lineno == 7
                assert entry.colno == line.index(expression)
                assert entry.end_colno == line.index(expression) + len(expression)
            else:
                assert False


def test_direct_super_proxy_class_lookup_once():
    events = []

    class Base:
        value = 42

        def method(self):
            return self

    class Derived(Base):
        pass

    class Proxy:
        @property
        def __class__(self):
            events.append("class")
            return Derived

    proxy = Proxy()
    for _ in range(5):
        events.clear()
        assert super(Derived, proxy).value == 42
        assert events == ["class"]
        events.clear()
        assert super(Derived, proxy).method() is proxy
        assert events == ["class"]


def test_direct_super_fallback_after_warmup():
    events = []

    class Base:
        @property
        def value(self):
            return self

        def method(self):
            return self

    class Proxy:
        @property
        def __class__(self):
            events.append("class")
            return Derived

    for constructor in ("super()", "super(Derived, self)"):
        for attribute in ("value", "method()"):
            for use_proxy in (False, True):
                # Give each scenario its own bytecode so an earlier bailout cannot hide a
                # transition from the fast path to the generic specialization.
                namespace = {"Base": Base}
                exec("class Derived(Base):\n"
                     "    def lookup(self):\n"
                     f"        return {constructor}.{attribute}\n", namespace)
                Derived = namespace["Derived"]
                lookup = Derived.lookup
                obj = Derived()
                for _ in range(10):
                    assert lookup(obj) is obj

                events.clear()
                if use_proxy:
                    proxy = Proxy()
                    assert lookup(proxy) is proxy
                    assert events == ["class"]
                else:
                    try:
                        lookup(object())
                    except TypeError:
                        pass
                    else:
                        assert False, "super() accepted an unrelated receiver"

                # Once attribute lookup falls back, subsequent calls must keep supplying real
                # super objects rather than returning the fast-path placeholder again.
                for _ in range(10):
                    assert lookup(obj) is obj
                assert events == (["class"] if use_proxy else [])


def test_direct_super_bound_descriptor_releases_receiver_after_call():
    refs = []

    class Base:
        @staticmethod
        def method(value):
            return value

    class Derived(Base):
        pass

    def make():
        obj = Derived()
        refs.append(weakref.ref(obj))
        return obj

    def argument():
        # A staticmethod does not retain its receiver. It may already be collected,
        # but the selected callable must remain valid while arguments are evaluated.
        util.gc_collect()
        return 42

    for _ in range(5):
        assert super(Derived, make()).method(argument()) == 42
        util.gc_collect(lambda: refs[-1]() is not None)
        assert refs[-1]() is None

    alias = super
    assert alias(Derived, make()).method(argument()) == 42
    util.gc_collect(lambda: refs[-1]() is not None)
    assert refs[-1]() is None

    class Caller:
        def call(self):
            return super(Caller, self).method(argument())

    namespace = globals()
    previous = namespace.get("super")
    had_previous = "super" in namespace
    try:
        namespace["super"] = lambda *args: make()
        assert Caller().call() == 42
    finally:
        if had_previous:
            namespace["super"] = previous
        else:
            namespace.pop("super", None)
    util.gc_collect(lambda: refs[-1]() is not None)
    assert refs[-1]() is None


def test_direct_super_missing_lookup_key_equality():
    events = []

    class Key(str):
        def __hash__(self):
            return hash("missing")

        def __eq__(self, other):
            events.append(other)
            return False

    Base = type("Base", (), {Key("collision"): 42})

    class Derived(Base):
        pass

    obj = Derived()
    alias = super

    def direct():
        return super(Derived, obj).missing

    def generic():
        return alias(Derived, obj).missing

    # GraalPy may cache negative lookups and omit subsequent __eq__ calls. This is an
    # intentionally accepted difference from CPython, which currently calls __eq__ on
    # every negative lookup, even after warmup. Keep checking that behavior on CPython
    # so a change in a future version makes this test fail and prompts us to reconsider
    # the GraalPy behavior.
    for _ in range(10):
        for lookup in (generic, direct):
            events.clear()
            try:
                lookup()
            except AttributeError:
                pass
            else:
                assert False
            assert all(event == "missing" for event in events)
            if sys.implementation.name == "cpython":
                assert events, "CPython no longer calls __eq__ on every negative super lookup"


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


    def test_direct_super_optimized_patterns():
        class A:
            value = 42

            def method(self, *args):
                return self, args

        class B(A):
            def zero_attr(self):
                return super().value

            def explicit_attr(self):
                return super(B, self).value

            def zero_method(self):
                return super().method(1)

            def explicit_method(self):
                return super(B, self).method(1)

            def starred_method(self, args):
                return super().method(*args)

            def keyword_method(self):
                return super().method(value=1)

        obj = B()
        assert obj.zero_attr() == obj.explicit_attr() == 42
        assert obj.zero_method() == obj.explicit_method() == (obj, (1,))
        assert obj.starred_method((1, 2)) == (obj, (1, 2))
        try:
            obj.keyword_method()
        except TypeError:
            pass
        else:
            assert False


    def test_direct_super_runtime_rebinding():
        class A:
            value = "builtin"

        class B(A):
            def value(self):
                return super().value

        obj = B()
        for _ in range(5):
            assert obj.value() == "builtin"
        assert_contains_bytecode(B.value, "SuperGetAttribute")

        sentinel = type("Replacement", (), {"value": "replacement"})()
        namespace = globals()
        previous = namespace.get("super")
        had_previous = "super" in namespace
        try:
            namespace["super"] = lambda: sentinel
            assert obj.value() == "replacement"
            namespace.pop("super")
            assert obj.value() == "builtin"
        finally:
            if had_previous:
                namespace["super"] = previous
            else:
                namespace.pop("super", None)
