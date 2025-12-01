# Copyright (c) 2018, 2025, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import itertools
import sys
import os
import unittest
from tests import util


class ExceptionTest(unittest.TestCase):
    # Tests for the issue #23353: check that the currently handled exception
    # is correctly saved/restored in PyEval_EvalFrameEx().

    def test_except_throw(self):

        def store_raise_exc_generator():
            try:
                self.assertEqual(sys.exc_info()[0], None)
                yield
            except Exception as exc:
                # exception raised by gen.throw(exc)
                self.assertEqual(sys.exc_info()[0], ValueError)
                self.assertIsNone(exc.__context__)
                yield

                # ensure that the exception is not lost
                self.assertEqual(sys.exc_info()[0], ValueError)
                yield

                # we should be able to raise back the ValueError
                raise

        make = store_raise_exc_generator()
        next(make)

        try:
            raise ValueError()
        except Exception as exc:
            try:
                make.throw(exc)
            except Exception:
                pass

        next(make)
        with self.assertRaises(ValueError) as cm:
            next(make)
        self.assertIsNone(cm.exception.__context__)

        self.assertEqual(sys.exc_info(), (None, None, None))

    def test_except_next(self):
        def gen():
            self.assertEqual(sys.exc_info()[0], ValueError)
            yield "done"

        g = gen()
        try:
            raise ValueError
        except Exception:
            self.assertEqual(next(g), "done")
        self.assertEqual(sys.exc_info(), (None, None, None))

    # def test_except_gen_except(self):
    #     def gen():
    #         try:
    #             self.assertEqual(sys.exc_info()[0], None)
    #             yield
    #             # we are called from "except ValueError:", TypeError must
    #             # inherit ValueError in its context
    #             raise TypeError()
    #         except TypeError as exc:
    #             self.assertEqual(sys.exc_info()[0], TypeError)
    #             self.assertEqual(type(exc.__context__), ValueError)
    #         # here we are still called from the "except ValueError:"
    #         self.assertEqual(sys.exc_info()[0], ValueError)
    #         yield
    #         self.assertIsNone(sys.exc_info()[0])
    #         yield "done"

    #     g = gen()
    #     next(g)
    #     try:
    #         raise ValueError
    #     except Exception:
    #         next(g)

    #     self.assertEqual(next(g), "done")
    #     self.assertEqual(sys.exc_info(), (None, None, None))

    # def test_except_throw_exception_context(self):
    #     def gen():
    #         try:
    #             try:
    #                 self.assertEqual(sys.exc_info()[0], None)
    #                 yield
    #             except ValueError:
    #                 # we are called from "except ValueError:"
    #                 self.assertEqual(sys.exc_info()[0], ValueError)
    #                 raise TypeError()
    #         except Exception as exc:
    #             self.assertEqual(sys.exc_info()[0], TypeError)
    #             self.assertEqual(type(exc.__context__), ValueError)
    #         # we are still called from "except ValueError:"
    #         self.assertEqual(sys.exc_info()[0], ValueError)
    #         yield
    #         self.assertIsNone(sys.exc_info()[0])
    #         yield "done"

    #     g = gen()
    #     next(g)
    #     try:
    #         raise ValueError
    #     except Exception as exc:
    #         g.throw(exc)

    #     self.assertEqual(next(g), "done")
    #     self.assertEqual(sys.exc_info(), (None, None, None))

    def test_throw_single_arg(self):
        def gen(log):
            log.append(1)
            try:
                yield
                log.append(2)
            except ValueError as e:
                self.assertEqual(e.args[0], "hello")
            yield
            log.append(3)
            return

        log = []
        g = gen(log)
        next(g)
        g.throw(ValueError, "hello")
        try:
            next(g)
        except StopIteration:
            pass
        self.assertEqual([1, 3], log)


    def test_throw_multiple_args(self):
        def gen(log):
            log.append(1)
            try:
                yield
                log.append(2)
            except ValueError as e:
                self.assertEqual(e.args, ("hello", "world", 42))
            yield
            log.append(3)
            return

        log = []
        g = gen(log)
        next(g)
        g.throw(ValueError, ("hello", "world", 42))
        try:
            next(g)
        except StopIteration:
            pass
        self.assertEqual([1, 3], log)


    def test_throw_exception_type(self):
        def gen(log):
            log.append(1)
            try:
                yield
                log.append(2)
            except ValueError as e:
                self.assertEqual(len(e.args), 0)
            yield
            log.append(3)
            return

        log = []
        g = gen(log)
        next(g)
        g.throw(ValueError)
        try:
            next(g)
        except StopIteration:
            pass
        self.assertEqual([1, 3], log)


    def test_gen_from_except(self):
        def gen():
            self.assertEqual(sys.exc_info()[0], None)
            yield

            try:
                raise TypeError
            except TypeError:
                # we are called from "except ValueError:"
                self.assertEqual(sys.exc_info()[0], TypeError)
            self.assertIsNone(sys.exc_info()[0])
            yield
            self.assertIsNone(sys.exc_info()[0])
            yield "done"

        try:
            raise ValueError
        except ValueError:
            self.assertEqual(sys.exc_info()[0], ValueError)
            g = gen()
        next(g)
        next(g)
        self.assertEqual(next(g), "done")

    @unittest.skipIf(sys.version_info.minor < 7, "Requires Python 3.7+")
    def test_stopiteration_error(self):
        # See also PEP 479.

        def gen():
            raise StopIteration
            yield

        with self.assertRaisesRegex(RuntimeError, 'raised StopIteration'):
            next(gen())

    @unittest.skipIf(sys.version_info.minor < 7, "Requires Python 3.7+")
    def test_tutorial_stopiteration(self):
        # Raise StopIteration" stops the generator too:

        def f():
            yield 1
            raise StopIteration
            yield 2 # never reached

        g = f()
        self.assertEqual(next(g), 1)

        with self.assertRaisesRegex(RuntimeError, 'raised StopIteration'):
            next(g)


    def test_return_tuple(self):
        def g():
            return (yield 1)

        gen = g()
        self.assertEqual(next(gen), 1)
        with self.assertRaises(StopIteration) as cm:
            gen.send((2,))
        self.assertEqual(cm.exception.value, (2,))

    def test_return_stopiteration(self):
        def g():
            return (yield 1)

        gen = g()
        self.assertEqual(next(gen), 1)
        with self.assertRaises(StopIteration) as cm:
            gen.send(StopIteration(2))
        self.assertTrue(isinstance(cm.exception.value, StopIteration))
        self.assertEqual(cm.exception.value.value, 2)

    def test_yield_expr_value_without_send(self):
        def fn():
            yield (1,(yield 42))
        g = fn()
        self.assertEqual(next(g), 42)
        self.assertEqual(next(g), (1,None))

    def test_generator_caller_frame(self):
        def gen():
            yield sys._getframe(1)
            yield sys._getframe(1)

        def callnext(g):
            next(g)
            return next(g)

        def callsend(g):
            next(g)
            return g.send(1)

        self.assertEqual(callnext(gen()).f_code.co_name, 'callnext')
        self.assertEqual(callsend(gen()).f_code.co_name, 'callsend')

        # Force a megamorphic call to the genrator function
        def genfn(i):
            l = {}
            exec(f"def f{i}(): yield {i}", l)
            return l[f'f{i}']

        fns = [genfn(i) for i in range(100)]
        fns.append(gen)
        for fn in fns:
            g = fn()
        self.assertEqual(callnext(g).f_code.co_name, 'callnext')
        for fn in fns:
            g = fn()
        self.assertEqual(callsend(g).f_code.co_name, 'callsend')

if sys.version_info.minor == 4 and sys.version_info.micro < 3:
    del ExceptionTest


def test_generator_starargs():
    def func(*args):
        return set(args)

    lst = [x for x in range(10)]
    gen = (x for x in range(10))
    assert func(*lst) == set(lst)
    assert func(*gen) == set(lst)


def test_generator_cell_confusion():
    # tfel: this demonstrates various errors we can get when we get confused
    # about the generator scopes and their parents for eager iterator
    # evaluation. In fact, all of these should work
    def unbound_local_l2():
        l1 = []
        l2 = []
        return [
            link for link in (
                (url for url in l1),
                (url for url in l2)
            )
        ]

    assert len(unbound_local_l2()) == 2

    def assertion_error_getting_closure_from_locals():
        l1 = []
        l2 = []
        l3 = []
        return [
            link for link in (
                (url for url in l1),
                (url for url in l2),
                (url for url in l3),
            )
        ]

    assert len(assertion_error_getting_closure_from_locals()) == 3

    def illegal_state_expected_cell_got_list():
        l11, l1 = [], []
        l22, l2 = [], []
        return [
            link for link in (
                (url for url in l1),
                (url for url in l2),
            )
        ]

    assert len(illegal_state_expected_cell_got_list()) == 2

def test_generator_exceptions_finally():
    def get_exc_state():
        assert sys.exc_info()[1] == sys.exception()
        return sys.exception()

    def generator():
        yield get_exc_state() # 1
        try:
            yield get_exc_state() # 2
            3 / 0
        except:
            yield get_exc_state() # 3
            yield get_exc_state() # 4
        finally:
            yield get_exc_state() # 5
            try:
                raise NameError()
            except:
                yield get_exc_state() # 6
                try:
                    raise KeyError()
                except:
                    yield get_exc_state() # 7
                yield get_exc_state() # 8
            yield get_exc_state() # 9
        yield get_exc_state() # 10

    def run_test(check_caller_ex):
        g = generator()
        assert check_caller_ex(g.send(None)) # 1
        assert check_caller_ex(g.send(None)) # 2
        assert type(g.send(None)) == ZeroDivisionError # 3
        assert type(g.send(None)) == ZeroDivisionError # 4
        assert check_caller_ex(g.send(None)) # 5
        assert type(g.send(None)) == NameError # 6
        assert type(g.send(None)) == KeyError # 7
        assert type(g.send(None)) == NameError # 8
        assert check_caller_ex(g.send(None)) # 9
        assert check_caller_ex(g.send(None)) # 10

    run_test(lambda x: x is None)
    try:
        raise NotImplementedError()
    except:
        run_test(lambda x: type(x) == NotImplementedError)


def test_generator_exceptions_complex():
    def get_exc_state():
        assert sys.exc_info()[1] == sys.exception()
        return sys.exception()

    def generator():
        yield get_exc_state() # 1
        try:
            yield get_exc_state() # 2
            3 / 0
        except:
            yield get_exc_state() # 3
            yield get_exc_state() # 4
        yield get_exc_state() # 5
        yield get_exc_state() # 6
        yield get_exc_state() # 7
        try:
            yield get_exc_state() # 8
            raise KeyError()
        except:
            yield get_exc_state() # 9
            yield get_exc_state() # 10
        yield get_exc_state() # 11
        try:
            raise NameError()
        except:
            yield get_exc_state() # 12
            try:
                raise NotImplementedError()
            except:
                yield get_exc_state() # 13
            yield get_exc_state() # 14
        yield get_exc_state() # 15
        yield get_exc_state() # 16

    g = generator()
    try:
        raise AttributeError()
    except:
        assert type(g.send(None)) == AttributeError # 1
        assert type(g.send(None)) == AttributeError # 2
        assert type(g.send(None)) == ZeroDivisionError # 3
        assert type(g.send(None)) == ZeroDivisionError # 4
        assert type(g.send(None)) == AttributeError # 5
    assert g.send(None) is None # 6
    try:
        raise IndexError()
    except:
        assert type(g.send(None)) == IndexError # 7
        assert type(g.send(None)) == IndexError # 8
        assert type(g.send(None)) == KeyError # 9
        assert type(g.send(None)) == KeyError # 10
        assert type(g.send(None)) == IndexError # 11
    try:
        raise TypeError()
    except:
        assert type(g.send(None)) == NameError # 12
        assert type(g.send(None)) == NotImplementedError # 13
        assert type(g.send(None)) == NameError # 14
        assert type(g.send(None)) == TypeError # 15
    assert g.send(None) is None # 16

    g = generator()
    assert g.send(None) is None # 1
    assert g.send(None) is None # 2
    assert type(g.send(None)) == ZeroDivisionError # 3
    assert type(g.send(None)) == ZeroDivisionError # 4
    assert g.send(None) is None # 5
    assert g.send(None) is None # 6
    assert g.send(None) is None # 7
    assert g.send(None) is None # 8
    assert type(g.send(None)) == KeyError # 9
    assert type(g.send(None)) == KeyError # 10
    assert g.send(None) is None # 11
    assert type(g.send(None)) == NameError # 12
    assert type(g.send(None)) == NotImplementedError # 13
    assert type(g.send(None)) == NameError # 14
    assert g.send(None) is None # 15
    assert g.send(None) is None # 16


def test_generator_gi_yieldfrom_multiple():
    # the same test as in CPython test suite, but with multiple yield from
    def a():
        yield

    def q():
        yield

    def b():
        yield from a()
        yield
        yield from q()
        yield

    gen_b = b()
    assert gen_b.gi_yieldfrom is None

    gen_b.send(None)
    assert gen_b.gi_yieldfrom.gi_code.co_name == 'a'

    gen_b.send(None)
    assert gen_b.gi_yieldfrom is None

    gen_b.send(None)
    assert gen_b.gi_yieldfrom.gi_code.co_name == 'q'

    gen_b.send(None)
    assert gen_b.gi_yieldfrom is None


def _test_generator_frame(checks):
    def gen():
        a = 1
        yield
        b = 2
        yield
        c = 3

    g = gen()
    f = None
    if checks[0]:
        f = g.gi_frame
        assert f
        assert f.f_back is None
        assert f.f_globals == globals()
        assert f.f_locals == {}
        assert f.f_lineno == gen.__code__.co_firstlineno
    next(g)
    if checks[1]:
        if not f:
            f = g.gi_frame
            assert f
        assert f.f_back is None
        assert f.f_globals == globals()
        assert f.f_locals == {'a': 1}
        assert f.f_lineno == gen.__code__.co_firstlineno + 2
        assert f is g.gi_frame
    next(g)
    if checks[2]:
        if not f:
            f = g.gi_frame
            assert f
        assert f.f_back is None
        assert f.f_globals == globals()
        assert f.f_locals == {'a': 1, 'b': 2}
        assert f.f_lineno == gen.__code__.co_firstlineno + 4
        assert f is g.gi_frame
    try:
        next(g)
        assert False
    except StopIteration:
        pass
    if f:
        assert f.f_back is sys._getframe()
        assert f.f_globals is globals()
        assert f.f_locals == {'a': 1, 'b': 2, 'c': 3}
        # TODO GR-61955
        if not util.IS_BYTECODE_DSL:
            assert f.f_lineno == gen.__code__.co_firstlineno + 5
    assert not g.gi_frame


def test_generator_frame():
    # Generte a whole matrix of possibilities of where the frame gets created vs re-synced
    for checks in itertools.product([False, True], repeat=3):
        _test_generator_frame(checks)


def test_generator_frame_from_getframe():
    def gen():
        a = 1
        yield sys._getframe()
        b = 2

    g = gen()
    f = next(g)
    assert f
    assert f.f_back is None
    assert f.f_globals == globals()
    assert f.f_locals == {'a': 1}
    assert f.f_lineno == gen.__code__.co_firstlineno + 2
    assert f is g.gi_frame
    try:
        next(g)
        assert False
    except StopIteration:
        pass
    assert f.f_back is sys._getframe()
    assert f.f_globals is globals()
    assert f.f_locals == {'a': 1, 'b': 2}
    # TODO GR-61955
    if not util.IS_BYTECODE_DSL:
        assert f.f_lineno == gen.__code__.co_firstlineno + 3


def test_generator_frame_in_running_generator():
    def gen():
        self = yield
        assert self.gi_running
        assert not self.gi_suspended
        assert self.gi_frame.f_lineno == gen.__code__.co_firstlineno + 4
        assert self.gi_frame.f_globals == globals()
        assert self.gi_frame.f_locals.get('self') is self
        assert self.gi_frame is sys._getframe()

    g = gen()
    next(g)
    try:
        g.send(g)
        assert False
    except StopIteration:
        pass


def test_generator_frame_in_generator_up_stack():
    def validate(self):
        assert self.gi_running
        assert not self.gi_suspended
        assert self.gi_frame.f_lineno == gen.__code__.co_firstlineno + 2
        assert self.gi_frame.f_globals == globals()
        assert self.gi_frame.f_locals.get('self') is self
        assert self.gi_frame is sys._getframe(1)

    def gen():
        self = yield
        validate(self)

    g = gen()
    next(g)
    try:
        g.send(g)
        assert False
    except StopIteration:
        pass
