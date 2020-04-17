import unittest


def gen():
    try:
        yield 1
    except OSError:
        yield "gotya"
    yield 2


class GeneratorThrowTests(unittest.TestCase):
    def test_throw_unstarted(self):
        g = gen()
        self.assertRaises(OverflowError, lambda: g.throw(OverflowError))

    def test_throw_with_type(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError)
        except OverflowError as e:
            self.assertEqual(e.args, ())
        else:
            self.fail("Exception not thrown")

    def test_throw_with_type_exception(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError, OverflowError("value"))
        except OverflowError as e:
            self.assertEqual(e.args, ("value",))
        else:
            self.fail("Exception not thrown")

    def test_throw_with_exception(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError("value"))
        except OverflowError as e:
            self.assertEqual(e.args, ("value",))
        else:
            self.fail("Exception not thrown")

    def test_throw_with_type_value(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError, "value")
        except OverflowError as e:
            self.assertEqual(e.args, ("value",))
        else:
            self.fail("Exception not thrown")

    def test_throw_with_type_value_tuple(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError, ("1", "2"))
        except OverflowError as e:
            self.assertEqual(e.args, ("1", "2"))
        else:
            self.fail("Exception not thrown")

    def test_throw_with_type_value_exception_subclass(self):
        g = gen()
        next(g)
        try:
            g.throw(OverflowError, NameError("value"))
        except OverflowError as e:
            self.assertEqual(type(e.args[0]), NameError)
        else:
            self.fail("Exception not thrown")

    def test_throw_with_traceback(self):
        try:
            raise NameError
        except Exception as e:
            tb = e.__traceback__
        g = gen()
        next(g)
        try:
            g.throw(OverflowError, OverflowError("value"), tb)
        except OverflowError as e:
            self.assertEqual(e.args, ("value",))
            # The traceback is tested in test_traceback
        else:
            self.fail("Exception not thrown")

    def test_throw_and_catch(self):
        g = gen()
        self.assertEqual(next(g), 1)
        self.assertEqual(g.throw(OSError), "gotya")
        self.assertEqual(next(g), 2)
        self.assertRaises(OSError, lambda: g.throw(OSError))
