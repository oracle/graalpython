# Copyright (c) 2021, 2021, Oracle and/or its affiliates.
# Copyright (C) 1996-2020 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2


def test_class_attr_change():
    class A(object):
        counter = 0

    for i in range(10):
        A.counter += 1

    assert A.counter == 10


def test_class_attr_deleted():
    class A(object):
        counter = 0

    class B(A):
        counter = 1

    for i in range(10):
        B.counter += 1

    assert B.counter == 11
    assert A.counter == 0
    del B.counter
    assert B.counter == 0

    for i in range(10):
        A.counter += 1
    assert A.counter == 10


def test_class_attr_added():
    class A(object):
        counter = 0

    class B(A):
        pass

    for i in range(10):
        B.counter += 1

    assert B.counter == 10
    assert A.counter == 0
    B.counter = 1
    assert B.counter == 1

    for i in range(10):
        A.counter += 1
    assert A.counter == 10


def test_class_attr_add_del():
    class A:
        foo = 1

    class B(A):
        foo = 2

    class C(B):
        foo = 3

    C.foo += 1
    C.foo += 1
    C.foo += 1
    C.foo += 1
    C.foo += 1
    C.foo += 1
    C.foo += 1

    assert C.foo == 10
    del C.foo
    assert C.foo == 2
    del B.foo
    assert C.foo == 1
    B.foo = 5
    assert C.foo == 5
    C.foo = 10
    assert C.foo == 10


def test_class_assignment():
    class A:
        foo = 1

    class B(A):
        foo = 2

    a = A()
    assert a.foo == 1
    a.__class__ = B
    assert a.foo == 2
    b = B()
    assert b.foo == 2
    b.__class__ = A
    assert b.foo == 1
    assert type(a) == B
    assert type(b) == A

    try:
        a.__class__ = 1
    except TypeError:
        assert True
    else:
        assert False

    try:
        a.__class__ = object
    except TypeError:
        assert True
    else:
        assert False

    try:
        object().__class__ = object
    except TypeError:
        assert True
    else:
        assert False


def test_class_slots():
    class X():
        __slots__ = "_local__impl", "__dict__"

        def __init__(self):
            self._local__impl = 1
            self.foo = 12
            self.__dict__ = {"bar": 42}


    assert X().bar == 42
    assert X()._local__impl == 1
    try:
        X().foo
    except AttributeError:
        assert True
    else:
        assert False

    x = X()
    x.foo = 1
    assert x.foo == 1
    assert x.__dict__["foo"] == 1
    x.__dict__["_local__impl"] = 22
    assert x._local__impl == 1

    assert X.__dict__["_local__impl"].__get__(x, type(x)) == 1


def test_class_with_slots_assignment():
    class X():
        __slots__ = "a", "b"

    class Y():
        __slots__ = "a", "b"

    class Z():
        __slots__ = "b", "c"


    x = X()
    x.__class__ = Y
    assert type(x) == Y
    try:
        x.__class__ = Z
    except TypeError as e:
        assert True
    else:
        assert False

def test_mro_change_on_attr_access():
    eq_called = []
    class MyKey(object):        
        def __hash__(self):            
            return hash('mykey')
        def __eq__(self, other):
            eq_called.append(1)
            X.__bases__ = (Base2,)

    class Base(object):
        mykey = 'base 42'

    class Base2(object):
        mykey = 'base2 42'

    X = type('X', (Base,), {MyKey(): 5})
    assert X.mykey == 'base 42'
    assert eq_called == [1]
    
    # ----------------------------------
    class MyKey(object):        
        def __hash__(self):            
            return hash('mykey')
        def __eq__(self, other):
            X.__bases__ = (Base,)

    class Base(object):
        pass

    class Base2(object):
        mykey = '42'        

    X = type('X', (Base,Base2,), {MyKey(): 5})
    mk = X.mykey
    assert mk == '42'
    
    X = type('X', (Base2,), {MyKey(): 5})
    assert X.mykey == '42'

    # ----------------------------------
    class Base(object):
        mykey = 'from Base2'        

    class Base2(object):
        pass
        
    X = type('X', (Base2,), {MyKey(): 5})
    try:    
        assert X.mykey == '42'
    except AttributeError as e:
        assert True
    else:
        assert False


def test_subclass_propagation():
    # Test taken from CPython's test_desc, but modified to use non-slot attributes,
    # which are also interesting on GraalPython in combination with MRO shapes.
    class A(object):
        pass
    class B(A):
        pass
    class C(A):
        pass
    class D(B, C):
        pass

    def assert_hash_raises_type_error(x):
        try:
            call_hash(x)
        except TypeError as e:
            pass
        else:
            assert False

    # This will make the call monomorphic
    def call_hash(x):
        return x.myhash()

    for i in range(1,3):
        d = D()
        A.myhash = lambda self: 42
        assert call_hash(d) == 42
        C.myhash = lambda self: 314
        assert call_hash(d) == 314
        B.myhash = lambda self: 144
        assert call_hash(d) == 144
        D.myhash = lambda self: 100
        assert call_hash(d) == 100
        D.myhash = None
        assert_hash_raises_type_error(d)
        del D.myhash
        assert call_hash(d) == 144
        B.myhash = None
        assert_hash_raises_type_error(d)
        del B.myhash
        assert call_hash(d) == 314
        C.myhash = None
        assert_hash_raises_type_error(d)
        del C.myhash
        assert call_hash(d) == 42
        A.myhash = None
        assert_hash_raises_type_error(d)


def test_slots_mismatch():
    # NOTE: this is less of a test of some well defined Python behavior that we want to support
    # and more of a stress test that checks that in some weird corner cases we do not fail with
    # internal errors or produce some clearly incorrect results.
    def raises_type_err(code):
        try:
            code()
        except TypeError:
            pass
        else:
            assert False

    class Klass(float):
        pass

    x = Klass(14)

    Klass.__getattribute__ = Klass.__pow__
    # Attribute access actually calls __pow__ now, with 2 arguments,
    # which should be fine, it will just return NotImplemented
    assert x.bar == NotImplemented

    Klass.__getattribute__ = float.__setattr__
    # __setattr__ requires 3 arguments, calling it via attribute read
    # should give argument validation error (TypeError)
    raises_type_err(lambda: x.bar)

    # The same for unary slot __hash__:

    # __round__ accepts single argument, but it is a binary builtin
    Klass.__hash__ = float.__round__
    try:
        assert hash(x) == 14
    except AssertionError:
        raise
    except:
        # On MacOS & GraalPython this test is giving TypeError: 'NoneType' object cannot be interpreted as an int
        # We ignore this for now. Important is that we do not give wrong result and do not fail on some internal error
        pass

    # __getattribute__ needs both its arguments
    Klass.__hash__ = float.__getattribute__
    raises_type_err(lambda: hash(x))


def test_no_value_and_mro_shape():
    class A:
        def foo(self):
            return 42

    class B(A):
        pass

    B.foo = lambda self: 1
    del B.foo

    class C(B):
        pass

    c = C()
    assert c.foo() == 42