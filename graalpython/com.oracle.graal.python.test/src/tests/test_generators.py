# Copyright (c) 2018, 2020, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import sys
import unittest


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


    # def test_return_tuple(self):
    #     def g():
    #         return (yield 1)

    #     gen = g()
    #     self.assertEqual(next(gen), 1)
    #     with self.assertRaises(StopIteration) as cm:
    #         gen.send((2,))
    #     self.assertEqual(cm.exception.value, (2,))

    # def test_return_stopiteration(self):
    #     def g():
    #         return (yield 1)

    #     gen = g()
    #     self.assertEqual(next(gen), 1)
    #     with self.assertRaises(StopIteration) as cm:
    #         gen.send(StopIteration(2))
    #     self.assertTrue(isinstance(cm.exception.value, StopIteration))
    #     self.assertEqual(cm.exception.value.value, 2)

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
