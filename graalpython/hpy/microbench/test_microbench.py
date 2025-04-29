"""
These are not real tests, but microbenchmarks. The machinery to record the
timing and display the results is inside conftest.py
"""

import pytest
import _valgrind

API_PARAMS = [
    pytest.param('cpy', marks=pytest.mark.cpy),
    pytest.param('hpy', marks=pytest.mark.hpy)
    ]

@pytest.fixture(params=API_PARAMS)
def api(request):
    return request.param

@pytest.fixture
def simple(request, api):
    if api == 'cpy':
        import cpy_simple
        return cpy_simple
    elif api == 'hpy':
        import hpy_simple
        return hpy_simple
    else:
        assert False, 'Unkown param: %s' % request.param

@pytest.fixture
def N(request):
    n = 10000000
    if request.config.option.fast:
        n //= 100
    if request.config.option.slow:
        n *= 10
    if _valgrind.lib.is_running_on_valgrind():
        n //= 100
    return n


class TestModule:

    def test_noargs(self, simple, timer, N):
        with timer:
            for i in range(N):
                simple.noargs()

    def test_onearg_None(self, simple, timer, N):
        with timer:
            for i in range(N):
                simple.onearg(None)

    def test_onearg_int(self, simple, timer, N):
        with timer:
            for i in range(N):
                simple.onearg(i)

    def test_varargs(self, simple, timer, N):
        with timer:
            for i in range(N):
                simple.varargs(None, None)

    def test_call_with_tuple(self, simple, timer, N):
        def f(a, b):
            return a + b

        with timer:
            for i in range(N):
                simple.call_with_tuple(f, (1, 2))

    def test_call_with_tuple_and_dict(self, simple, timer, N):
        def f(a, b):
            return a + b

        with timer:
            for i in range(N):
                simple.call_with_tuple_and_dict(f, (1,), {"b": 2})

    def test_allocate_int(self, simple, timer, N):
        with timer:
            for i in range(N):
                simple.allocate_int()

    def test_allocate_tuple(self, api, simple, timer, N):
        with timer:
            for i in range(N):
                simple.allocate_tuple()


class TestType:
    """ Compares the performance of operations on types.

        The kinds of type used are:

        * cpy: a static type
        * hpy: a heap type (HPy only has heap types)

        The type is named `simple.Foo` in both cases.
    """

    def test_allocate_obj(self, simple, timer, N):
        import gc
        Foo = simple.Foo
        objs = [None] * N
        gc.collect()
        with timer:
            for i in range(N):
                objs[i] = Foo()
            del objs
            gc.collect()

    def test_method_lookup(self, simple, timer, N):
        obj = simple.Foo()
        with timer:
            for i in range(N):
                # note: here we are NOT calling it, we want to measure just
                # the lookup
                obj.noargs

    def test_noargs(self, simple, timer, N):
        obj = simple.Foo()
        with timer:
            for i in range(N):
                obj.noargs()

    def test_onearg_None(self, simple, timer, N):
        obj = simple.Foo()
        with timer:
            for i in range(N):
                obj.onearg(None)

    def test_onearg_int(self, simple, timer, N):
        obj = simple.Foo()
        with timer:
            for i in range(N):
                obj.onearg(i)

    def test_varargs(self, simple, timer, N):
        obj = simple.Foo()
        with timer:
            for i in range(N):
                obj.varargs(None, None)

    def test_len(self, simple, timer, N):
        obj = simple.Foo()
        with timer:
            for i in range(N):
                len(obj)

    def test_getitem(self, simple, timer, N):
        obj = simple.Foo()
        with timer:
            for i in range(N):
                obj[0]


class TestHeapType:
    """ Compares the performance of operations on heap types.

        The type is named `simple.HTFoo` and is a heap type in all cases.
    """

    def test_allocate_obj_and_survive(self, simple, timer, N):
        import gc
        HTFoo = simple.HTFoo
        objs = [None] * N
        gc.collect()
        with timer:
            for i in range(N):
                objs[i] = HTFoo()
            del objs
            gc.collect()

    def test_allocate_obj_and_die(self, simple, timer, N):
        import gc
        HTFoo = simple.HTFoo
        gc.collect()
        with timer:
            for i in range(N):
                obj = HTFoo()
                obj.onearg(None)
            gc.collect()

    def test_method_lookup(self, simple, timer, N):
        obj = simple.HTFoo()
        with timer:
            for i in range(N):
                # note: here we are NOT calling it, we want to measure just
                # the lookup
                obj.noargs

    def test_noargs(self, simple, timer, N):
        obj = simple.HTFoo()
        with timer:
            for i in range(N):
                obj.noargs()

    def test_onearg_None(self, simple, timer, N):
        obj = simple.HTFoo()
        with timer:
            for i in range(N):
                obj.onearg(None)

    def test_onearg_int(self, simple, timer, N):
        obj = simple.HTFoo()
        with timer:
            for i in range(N):
                obj.onearg(i)

    def test_varargs(self, simple, timer, N):
        obj = simple.HTFoo()
        with timer:
            for i in range(N):
                obj.varargs(None, None)

    def test_len(self, simple, timer, N):
        obj = simple.HTFoo()
        with timer:
            for i in range(N):
                len(obj)

    def test_getitem(self, simple, timer, N):
        obj = simple.HTFoo()
        with timer:
            for i in range(N):
                obj[0]
