# Copyright (c) 2018, Oracle and/or its affiliates.
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

    def test_stopiteration_warning(self):
        # See also PEP 479.

        def gen():
            raise StopIteration
            yield

        with self.assertRaises(StopIteration):
            next(gen())

    def test_tutorial_stopiteration(self):
        # Raise StopIteration" stops the generator too:

        def f():
            yield 1
            raise StopIteration
            yield 2 # never reached

        g = f()
        self.assertEqual(next(g), 1)

        with self.assertRaises(StopIteration):
            next(g)

        with self.assertRaises(StopIteration):
            # This time StopIteration isn't raised from the generator's body,
            # hence no warning.
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


if sys.version_info.minor == 4 and sys.version_info.micro < 3:
    del ExceptionTest


def test_unboundlocalerror_gen():
    def func(args):
        args = (arg for arg in args)
        return set(args)

    assert func([1, 2, 3, 4]) == {1, 2, 3, 4}
