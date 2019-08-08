# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_class_cell_members():
    class X(object):
        opts1 = ["a", "b", "c"]
        opts2 = [s for s in opts1]

    x = X()
    assert x.opts1 == ["a", "b", "c"]
    assert x.opts2 == ["a", "b", "c"]

    class X(object):
        def a(self):
            pass

        @staticmethod
        def b():
            pass

        @classmethod
        def c(cls):
            pass

        opts = [str(s) for s in (a, b, c)]

    x = X()
    assert x.opts[0].startswith("<function")
    assert x.opts[1].startswith("<staticmethod")
    assert x.opts[2].startswith("<classmethod")


a_global_var = "a global var"


def test_class_global_var_member():
    class C(object):
        a_global_var = a_global_var

    assert C.a_global_var == a_global_var


def test_class_local_var_member():
    a_local_var = "a local var"

    def f():
        class C(object):
            a_local_var = a_local_var

        return C.a_local_var

    assert_raises(NameError, f)


def test_simple_nesting():

    def make_adder(x):
        def adder(y):
            return x + y
        return adder

    inc = make_adder(1)
    plus10 = make_adder(10)

    assert inc(1) == 2
    assert plus10(-2) == 8


def test_extra_nesting():

    def make_adder2(x):
        def extra():  # check freevars passing through non-use scopes
            def adder(y):
                return x + y
            return adder
        return extra()

    inc = make_adder2(1)
    plus10 = make_adder2(10)

    assert inc(1) == 2
    assert plus10(-2) == 8


def test_simple_and_rebinding():

    def make_adder3(x):
        def adder(y):
            return x + y
        x = x + 1 # check tracking of assignment to x in defining scope
        return adder

    inc = make_adder3(0)
    plus10 = make_adder3(9)

    assert inc(1) == 2
    assert plus10(-2) == 8


def test_nesting_global_no_free():

    def make_adder4():  # XXX add exta level of indirection
        def nest():
            def nest():
                def adder(y):
                    return global_x + y # check that plain old globals work
                return adder
            return nest()
        return nest()

    global_x = 1
    adder = make_adder4()
    assert adder(1) == 2

    global_x = 10
    assert adder(-2) == 8


def test_nesting_through_class():

    def make_adder5(x):
        class Adder:
            def __call__(self, y):
                return x + y
        return Adder()

    inc = make_adder5(1)
    plus10 = make_adder5(10)

    assert inc(1) == 2
    assert plus10(-2) == 8


def test_nesting_plus_free_ref_to_global():

    def make_adder6(x):
        global global_nest_x

        def adder(y):
            return global_nest_x + y
        global_nest_x = x
        return adder

    inc = make_adder6(1)
    plus10 = make_adder6(10)

    assert inc(1) == 11  # there's only one global
    assert plus10(-2) == 8


def test_nearest_enclosing_scope():

    def f(x):
        def g(y):
            x = 42 # check that this masks binding in f()

            def h(z):
                return x + z
            return h
        return g(2)

    test_func = f(10)
    assert test_func(5) == 47


def test_mixed_freevars_and_cellvars():

    def identity(x):
        return x

    def f(x, y, z):
        def g(a, b, c):
            a = a + x # 3

            def h():
                # z * (4 + 9)
                # 3 * 13
                return identity(z * (b + y))
            y = c + z # 9
            return h
        return g

    g = f(1, 2, 3)
    h = g(2, 4, 6)
    assert h() == 39


def test_free_var_in_method():

    def test():
        method_and_var = "var"

        class Test:

            def method_and_var(self):
                return "method"

            def test(self):
                return method_and_var

            def actual_global(self):
                return str("global")

            def str(self):
                return str(self)
        return Test()

    t = test()
    assert t.test() == "var"
    assert t.method_and_var() == "method"
    assert t.actual_global() == "global"

    method_and_var = "var"

    class Test:
        # this class is not nested, so the rules are different

        def method_and_var(self):
            return "method"

        def test(self):
            return method_and_var

        def actual_global(self):
            return str("global")

        def str(self):
            return str(self)

    t = Test()
    assert t.test() == "var"
    assert t.method_and_var() == "method"
    assert t.actual_global() == "global"


def test_cell_is_kwonly_arg():
    # Issue 1409: Initialisation of a cell value,
    # when it comes from a keyword-only parameter
    def foo(*, a=17):
        def bar():
            return a + 5
        return bar() + 3

    assert foo(a=42) == 50
    assert foo() == 25


def test_recursion():

    def f(x):
        def fact(n):
            if n == 0:
                return 1
            else:
                return n * fact(n - 1)
        if x >= 0:
            return fact(x)
        else:
            raise ValueError("x must be >= 0")

    assert f(6) == 720


def test_lambdas():

    f1 = lambda x: lambda y: x + y
    inc = f1(1)
    plus10 = f1(10)
    assert inc(1) == 2
    assert plus10(5) == 15

    f2 = lambda x: (lambda : lambda y: x + y)()
    inc = f2(1)
    plus10 = f2(10)
    assert inc(1) == 2
    assert plus10(5) == 15

    f3 = lambda x: lambda y: global_x + y
    global_x = 1
    inc = f3(None)
    assert inc(2) == 3

    f8 = lambda x, y, z: lambda a, b, c: lambda : z * (b + y)
    g = f8(1, 2, 3)
    h = g(2, 4, 6)
    assert h() == 18


def test_unbound_local():

    def errorInOuter():
        print(y)

        def inner():
            return y
        y = 1

    def errorInInner():
        def inner():
            return y
        inner()
        y = 1

    assert_raises(UnboundLocalError, errorInOuter)
    assert_raises(NameError, errorInInner)


def test_unbound_local_after_del():
    # #4617: It is now legal to delete a cell variable.
    # The following functions must obviously compile,
    # and give the correct error when accessing the deleted name.
    def errorInOuter():
        y = 1
        del y
        print(y)
        def inner():
            return y

    def errorInInner():
        def inner():
            return y
        y = 1
        del y
        inner()

    assert_raises(UnboundLocalError, errorInOuter)
    assert_raises(NameError, errorInInner)


def test_Unbound_Local_AugAssign():
    # test for bug #1501934: incorrect LOAD/STORE_GLOBAL generation
    exec("""if 1:
        global_x = 1
        def f():
            global_x += 1
        try:
            f()
        except UnboundLocalError:
            pass
        else:
            fail('scope of global_x not correctly determined')
        """)


def test_complex_definitions():

    def makeReturner(*lst):
        def returner():
            return lst
        return returner

    assert makeReturner(1,2,3)() == (1,2,3)

    def makeReturner2(**kwargs):
        def returner():
            return kwargs
        return returner

    assert makeReturner2(a=11)()['a'] == 11


# def test_scope_of_global_stmt():
#     # Examples posted by Samuele Pedroni to python-dev on 3/1/2001
#
#     exec("""if 1:
#         # I
#         x = 7
#         def f():
#             x = 1
#             def g():
#                 global x
#                 def i():
#                     def h():
#                         return x
#                     return h()
#                 return i()
#             return g()
#         assert f() == 7
#         assert x == 7
#         # II
#         x = 7
#         def f():
#             x = 1
#             def g():
#                 x = 2
#                 def i():
#                     def h():
#                         return x
#                     return h()
#                 return i()
#             return g()
#         assert f() == 2
#         assert x == 7
#         # III
#         x = 7
#         def f():
#             x = 1
#             def g():
#                 global x
#                 x = 2
#                 def i():
#                     def h():
#                         return x
#                     return h()
#                 return i()
#             return g()
#         assert f() == 2
#         assert x == 2
#         # IV
#         x = 7
#         def f():
#             x = 3
#             def g():
#                 global x
#                 x = 2
#                 def i():
#                     def h():
#                         return x
#                     return h()
#                 return i()
#             return g()
#         assert f() == 2
#         assert x == 2
#         # XXX what about global statements in class blocks?
#         # do they affect methods?
#         x = 12
#         class Global:
#             global x
#             x = 13
#             def set(self, val):
#                 x = val
#             def get(self):
#                 return x
#         g = Global()
#         assert g.get() == 13
#         g.set(15)
#         assert g.get() == 13
#         """)


def test_leaks():

    class Foo:
        count = 0

        def __init__(self):
            Foo.count += 1

        def __del__(self):
            Foo.count -= 1

    def f1():
        x = Foo()

        def f2():
            return x
        f2()

        del x

    for i in range(100):
        f1()

    # TODO: in cpython the value for count is 0 since "x" is gced after each f1() call
    assert Foo.count == 0 or Foo.count == 100


# def test_class_and_global():
#
#     exec("""if 1:
#         def test(x):
#             class Foo:
#                 global x
#                 def __call__(self, y):
#                     return x + y
#             return Foo()
#         x = 0
#         assert test(6)(2) == 8
#         x = -1
#         assert test(3)(2) == 5
#         looked_up_by_load_name = False
#         class X:
#             # Implicit globals inside classes are be looked up by LOAD_NAME, not
#             # LOAD_GLOBAL.
#             locals()['looked_up_by_load_name'] = True
#             passed = looked_up_by_load_name
#         assert X.passed == True
#         """)


def test_modify_locals():
    x = 1
    assert set(locals().keys()) == {'x'}
    ll = locals()
    assert set(locals().keys()) == {'x', 'll'}
    ll['a'] = 2
    ll['b'] = 3
    assert 'x' in locals()
    assert 'll' in locals()
    assert locals()['a'] == 2
    assert locals()['b'] == 3
    assert set(locals().keys()) == {'x', 'll', 'a', 'b'}
    y = 4
    assert set(locals().keys()) == {'x', 'll', 'a', 'b', 'y'}


def test_locals_function():

    def f(x):
        def g(y):
            def h(z):
                return y + z
            w = x + y
            y += 3
            return locals()
        return g

    d = f(2)(4)
    assert 'h' in d
    del d['h']
    assert d == {'x': 2, 'y': 7, 'w': 6}


def test_locals_class():
    # This test verifies that calling locals() does not pollute
    # the local namespace of the class with free variables.  Old
    # versions of Python had a bug, where a free variable being
    # passed through a class namespace would be inserted into
    # locals() by locals() or exec or a trace function.
    #
    # The real bug lies in frame code that copies variables
    # between fast locals and the locals dict, e.g. when executing
    # a trace function.

    def f(x):
        class C:
            x = 12

            def m(self):
                return x
            locals()
        return C

    assert f(1).x == 12

    def f(x):
        class C:
            y = x

            def m(self):
                return x
            z = list(locals())
        return C

    varnames = f(1).z
    assert "x" not in varnames
    assert "y" in varnames


def test_bound_and_free():
    # var is bound and free in class

    def f(x):
        class C:
            def m(self):
                return x
            a = x
        return C

    inst = f(3)()
    assert inst.a == inst.m()


# def test_eval_exec_free_vars():
#
#     def f(x):
#         return lambda: x + 1
#
#     g = f(3)
#     assert_raises(TypeError, eval, g.__code__)
#
#     try:
#         exec(g.__code__, {})
#     except TypeError:
#         pass
#     else:
#         raise AssertionError("exec should have failed, because code contained free vars")


def test_list_comp_local_vars():

    try:
        print(bad)
    except NameError:
        pass
    else:
        print("bad should not be defined")

    def x():
        [bad for s in 'a b' for bad in s.split()]

    x()
    try:
        print(bad)
    except NameError:
        pass


def test_eval_free_vars():

    def f(x):
        def g():
            x
            eval("x + 1")
        return g

    f(4)()


def test_nonLocal_function():

    def f(x):

        def inc():
            nonlocal x
            x += 1
            return x

        def dec():
            nonlocal x
            x -= 1
            return x
        return inc, dec

    inc, dec = f(0)
    assert inc() == 1
    assert inc() == 2
    assert dec() == 1
    assert dec() == 0


def test_nonlocal_method():
    def f(x):
        class c:

            def inc(self):
                nonlocal x
                x += 1
                return x

            def dec(self):
                nonlocal x
                x -= 1
                return x
        return c()
    c = f(0)
    assert c.inc() == 1
    assert c.inc() == 2
    assert c.dec() == 1
    assert c.dec() == 0


# def test_global_in_parallel_nested_functions():
#     # A symbol table bug leaked the global statement from one
#     # function to other nested functions in the same block.
#     # This test verifies that a global statement in the first
#     # function does not affect the second function.
#     local_ns = {}
#     global_ns = {}
#     exec("""if 1:
#         def f():
#             y = 1
#             def g():
#                 global y
#                 return y
#             def h():
#                 return y + 1
#             return g, h
#         y = 9
#         g, h = f()
#         result9 = g()
#         result2 = h()
#         """, local_ns, global_ns)
#     assert 2 == global_ns["result2"]
#     assert 9 == global_ns["result9"]


def test_nonlocal_class():

    def f(x):
        class c:
            nonlocal x
            x += 1

            def get(self):
                return x
        return c()

    c = f(0)
    assert c.get() == 1
    assert "x" not in c.__class__.__dict__


def test_nonlocal_generator():

    def f(x):
        def g(y):
            nonlocal x
            for i in range(y):
                x += 1
                yield x
        return g

    g = f(0)
    assert list(g(5)) == [1, 2, 3, 4, 5]


def test_nested_nonlocal():

    def f(x):
        def g():
            nonlocal x
            x -= 2

            def h():
                nonlocal x
                x += 4
                return x
            return h
        return g

    g = f(1)
    h = g()
    assert h() == 3


def test_top_is_not_significant():
    # See #9997.
    def top(a):
        pass

    def b():
        global a


def test_class_namespace_overrides_closure():
    # See #17853.
    x = 42
    class X:
        locals()["x"] = 43
        y = x
    assert X.y == 43
    class X:
        locals()["x"] = 43
        del x
    assert not hasattr(X, "x")
    assert x == 42


def test_nonlocal_cell():
    class A(object):
        def method(self):
            nonlocal done
            done = True

    done = False
    a = A()
    assert not done
    a.method()
    assert done


def test_import_name():
    from os import environ

    def func_1(a):
        def func_2(x=environ):
            x['my-key'] = a
            return x
        return func_2()

    val = func_1('test-val')
    assert 'my-key' in val
    assert val['my-key'] == 'test-val'
    del environ['my-key']


def test_class_attr():
    class MyClass(object):
        a_counter = 0

    def register():
        MyClass.a_counter += 1

    MyClass.a_counter += 1
    MyClass.a_counter += 1
    MyClass.a_counter += 1
    MyClass.a_counter += 1

    for i in range(10):
        MyClass.a_counter += 1

    for i in range(10):
        register()

    assert MyClass.a_counter == 24


def test_generator_func_with_nested_nonlocals():
    def b_func():
        exec_gen = False

        def _inner_func():
            def doit():
                nonlocal exec_gen
                exec_gen = True
                return [1]

            assert set(doit.__code__.co_cellvars) == set()
            assert set(doit.__code__.co_freevars) == {'exec_gen'}
            for A in doit():
                for C in Y:
                    yield A

        assert set(_inner_func.__code__.co_cellvars) == set()
        assert set(_inner_func.__code__.co_freevars) == {'Y', 'exec_gen'}

        gen = _inner_func()
        assert not exec_gen

        Y = [1, 2]

        list(gen)
        assert exec_gen
        return gen

    assert set(b_func.__code__.co_cellvars) == {'Y', 'exec_gen'}
    assert set(b_func.__code__.co_freevars) == set()
    b_func()


def test_generator_scope():
    my_obj = [1, 2, 3, 4]
    my_obj = (i for i in my_obj for j in y)
    y = [1, 2]

    assert set(my_obj.gi_code.co_cellvars) == set()
    assert set(my_obj.gi_code.co_freevars) == {'y'}


def test_func_scope():
    my_obj = [1, 2, 3, 4]

    def my_obj():
        return [i for i in my_obj for j in y]

    y = [1, 2]

    assert set(my_obj.__code__.co_cellvars) == set()
    assert set(my_obj.__code__.co_freevars) == {'my_obj', 'y'}


def test_classbody_scope():
    class A():
        ranges = [(1, 10)]

        class B():
            ranges = [(2, 12)]

        class C():
            ranges = [ ]

            class CA():
                ranges = [(3, 13)]

        locs = locals()

        class D(B, C):
            pass


    A.C.ranges = (A.C.CA.ranges)
    assert A.C.ranges == A.C.CA.ranges == [(3, 13)]
    locs = list(A.locs.keys())
    for k in ["__module__", "__qualname__", "ranges", "B", "C", "locs", "D"]:
        assert k in locs, locs
