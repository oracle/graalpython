# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import _decimal as decimal
import _pydecimal as reference
import asyncio
import contextvars
import copy
import decimal as public_decimal
import gc
import inspect
import locale
import math
import numbers
import operator
import pickle
import random
import sys
import threading
import unittest
import warnings
import weakref
from collections.abc import MutableMapping
from fractions import Fraction


# Both implementations expose rounding modes as strings.
ROUNDINGS = ("ROUND_DOWN", "ROUND_UP", "ROUND_CEILING", "ROUND_FLOOR",
             "ROUND_HALF_UP", "ROUND_HALF_DOWN", "ROUND_HALF_EVEN", "ROUND_05UP")
SIGNALS = (decimal.InvalidOperation, decimal.FloatOperation, decimal.DivisionByZero,
           decimal.Overflow, decimal.Underflow, decimal.Subnormal, decimal.Inexact,
           decimal.Rounded, decimal.Clamped)


def enabled_flags(context):
    return sorted(signal.__name__ for signal, enabled in context.flags.items() if enabled)



class DecimalInitializationTests(unittest.TestCase):
    def test_exception_hierarchy(self):
        bases = {
            "DecimalException": (ArithmeticError,),
            "InvalidOperation": (decimal.DecimalException,),
            "FloatOperation": (decimal.DecimalException, TypeError),
            "DivisionByZero": (decimal.DecimalException, ZeroDivisionError),
            "Inexact": (decimal.DecimalException,),
            "Rounded": (decimal.DecimalException,),
            "Subnormal": (decimal.DecimalException,),
            "Overflow": (decimal.Inexact, decimal.Rounded),
            "Underflow": (decimal.Inexact, decimal.Rounded, decimal.Subnormal),
            "Clamped": (decimal.DecimalException,),
            "ConversionSyntax": (decimal.InvalidOperation,),
            "DivisionImpossible": (decimal.InvalidOperation,),
            "DivisionUndefined": (decimal.InvalidOperation, ZeroDivisionError),
            "InvalidContext": (decimal.InvalidOperation,),
        }
        for name, expected in bases.items():
            with self.subTest(name=name):
                cls = getattr(decimal, name)
                self.assertEqual(cls.__bases__, expected)
                self.assertEqual(cls.__module__, "decimal")
                exc = cls("message", 42)
                self.assertEqual(exc.args, ("message", 42))
                self.assertEqual(str(exc), "('message', 42)")
                self.assertEqual(repr(exc), f"{name}('message', 42)")
                try:
                    raise exc
                except ArithmeticError as caught:
                    self.assertIs(caught, exc)
        self.assertTrue(issubclass(decimal.FloatOperation, TypeError))
        self.assertTrue(issubclass(decimal.DivisionUndefined, ZeroDivisionError))
        self.assertTrue(issubclass(decimal.Underflow, decimal.Subnormal))

    def test_public_types_and_builtins(self):
        self.assertEqual(decimal.Decimal.__module__, "decimal")
        self.assertEqual(decimal.Context.__module__, "decimal")
        self.assertIs(decimal.Decimal.exp.__objclass__, decimal.Decimal)
        self.assertIs(decimal.Context.prec.__objclass__, decimal.Context)
        self.assertIn("__new__", vars(decimal.Decimal))
        self.assertIn("__new__", vars(decimal.Context))

    def test_decimal_tuple(self):
        cls = decimal.DecimalTuple
        self.assertEqual(cls.__bases__, (tuple,))
        self.assertEqual(cls.__module__, "decimal")
        self.assertEqual(cls._fields, ("sign", "digits", "exponent"))
        self.assertEqual(cls.__match_args__, cls._fields)
        self.assertEqual(cls._field_defaults, {})
        self.assertIs(cls._make.__self__, cls)
        self.assertEqual(cls.__slots__, ())
        self.assertEqual(cls.__doc__, "DecimalTuple(sign, digits, exponent)")
        for name in ("__getitem__", "__len__", "__hash__", "__iter__"):
            self.assertIs(getattr(cls, name), getattr(tuple, name))
        value = cls(0, (1, 2), -1)
        self.assertEqual(value, (0, (1, 2), -1))
        self.assertEqual(cls(sign=0, digits=(1, 2), exponent=-1), value)
        self.assertEqual((value.sign, value.digits, value.exponent), tuple(value))
        self.assertEqual(repr(value), "DecimalTuple(sign=0, digits=(1, 2), exponent=-1)")
        self.assertEqual(hash(value), hash(tuple(value)))
        self.assertFalse(hasattr(value, "__dict__"))
        self.assertIs(type(cls._make(iter(value))), cls)
        self.assertEqual(cls._make(value), value)
        self.assertEqual(value._asdict(), {"sign": 0, "digits": (1, 2), "exponent": -1})
        self.assertEqual(value.__getnewargs__(), tuple(value))
        self.assertEqual(value._replace(sign=1), cls(1, (1, 2), -1))
        self.assertEqual(value.__replace__(exponent=2), cls(0, (1, 2), 2))
        with self.assertRaises(AttributeError):
            value.sign = 1
        with self.assertRaises(TypeError):
            cls._make((0, (1, 2)))
        with self.assertRaises(TypeError):
            value._replace(unknown=1)
        with self.assertRaises(TypeError):
            cls(0, (1, 2))

    def test_decimal_tuple_subclass_and_copy(self):
        class TupleSubclass(decimal.DecimalTuple):
            __slots__ = ()

        value = TupleSubclass(1, (4, 2), -2)
        self.assertIs(type(TupleSubclass._make(value)), TupleSubclass)
        self.assertIs(type(value._replace(sign=0)), TupleSubclass)
        self.assertEqual(repr(value), "TupleSubclass(sign=1, digits=(4, 2), exponent=-2)")
        for copied in (copy.copy(value), copy.deepcopy(value)):
            self.assertIs(type(copied), TupleSubclass)
            self.assertEqual(copied, value)

    def test_decimal_tuple_pickle(self):
        value = decimal.DecimalTuple(0, (1, 2), -1)
        for protocol in range(pickle.HIGHEST_PROTOCOL + 1):
            restored = pickle.loads(pickle.dumps(value, protocol))
            self.assertIs(type(restored), decimal.DecimalTuple)
            self.assertEqual(restored, value)

    def test_signal_dict_inheritance(self):
        # These are internal types retained by the module state.
        mixin, = (cls for cls in object.__subclasses__() if cls.__name__ == "SignalDictMixin" and cls.__module__ == "decimal")
        signal_dict, = mixin.__subclasses__()
        self.assertEqual(signal_dict.__bases__, (mixin, MutableMapping))
        self.assertIs(signal_dict.items, MutableMapping.items)
        self.assertIs(signal_dict.update, MutableMapping.update)
        self.assertIs(signal_dict.copy, mixin.copy)
        self.assertIsNone(signal_dict.__hash__)
        for name in ("__getitem__", "__setitem__", "__delitem__", "__len__", "__iter__", "__repr__", "__eq__"):
            self.assertIs(getattr(signal_dict, name), getattr(mixin, name))


class DecimalConstructionTests(unittest.TestCase):
    def test_module_constants(self):
        for name in ROUNDINGS:
            self.assertEqual(getattr(decimal, name), name)
        self.assertEqual(decimal.MAX_PREC, 999999999999999999)
        self.assertEqual(decimal.MAX_EMAX, decimal.MAX_PREC)
        self.assertEqual(decimal.MIN_EMIN, -decimal.MAX_EMAX)
        self.assertEqual(decimal.MIN_ETINY, -1999999999999999997)
        self.assertIs(decimal.HAVE_THREADS, True)
        self.assertIs(decimal.HAVE_CONTEXTVAR, True)
        self.assertEqual(decimal.__version__, "1.70")
        self.assertIsInstance(decimal.__libmpdec_version__, str)

    def test_default_and_integer(self):
        self.assertEqual(repr(decimal.Decimal()), "Decimal('0')")
        for value in (0, 42, -42, True, False, -(2**31), 2**31 - 1, 2**31, -(2**31) - 1,
                      2**63 - 1, 2**63, -2**63, -2**63 + 1, -(10**100), 10**100):
            with self.subTest(value=value):
                self.assertEqual(repr(decimal.Decimal(value)), f"Decimal('{int(value)}')")

    def test_strings(self):
        cases = {
            "00012.3400": "12.3400",
            "-0": "-0",
            "-0.000": "-0.000",
            ".000001": "0.000001",
            ".0000001": "1E-7",
            "1.2300e-7": "1.2300E-7",
            "1.2300e2": "123.00",
            "1.2300e5": "1.2300E+5",
            "0e-7": "0E-7",
            "-0e+10": "-0E+10",
            "1e3000000000": "1E+3000000000",
            "1e999999999999999999": "1E+999999999999999999",
            "1e-1999999999999999997": "1E-1999999999999999997",
            "1e00000000000000000000000000000001": "1E+1",
            "  +12.300  ": "12.300",
            "\x1c12\x1c": "12",
            "\u00a0\u0661\u0662.\u0663\uff10\u2002": "12.30",
            "\U0001d7d9.\U0001d7da": "1.2",
            "_1_._2_e_+_3_": "1.2E+3",
            "iNf": "Infinity",
            "-infinity": "-Infinity",
            "+NaN": "NaN",
            "-nan00120": "-NaN120",
            "NaN000": "NaN",
            "-sNaN0120": "-sNaN120",
            "sNaN0": "sNaN",
        }
        for value, expected in cases.items():
            with self.subTest(value=value):
                self.assertEqual(repr(decimal.Decimal(value)), f"Decimal('{expected}')")

    def test_exact_float_conversion(self):
        cases = {
            0.1: "0.1000000000000000055511151231257827021181583404541015625",
            1.5: "1.5",
            -1.5: "-1.5",
            float("inf"): "Infinity",
            float("-inf"): "-Infinity",
        }
        for value, expected in cases.items():
            self.assertEqual(repr(decimal.Decimal(value)), f"Decimal('{expected}')")
        self.assertEqual(repr(decimal.Decimal(0.0)), "Decimal('0')")
        self.assertEqual(repr(decimal.Decimal(-0.0)), "Decimal('-0')")
        self.assertEqual(repr(decimal.Decimal(float("nan"))), "Decimal('NaN')")

    def test_exact_float_coefficient_boundaries(self):
        cases = [
            ("0x1p-3", "0.125"),
            ("0x1p-27", "7.450580596923828125E-9"),
            ("0x1p-28", "3.7252902984619140625E-9"),
            ("0x1p+62", "4611686018427387904"),
            ("0x1.fffffffffffffp+62", "9223372036854774784"),
            ("0x1p+63", "9223372036854775808"),
            ("0x1.8000000000001p+0", "1.5000000000000002220446049250313080847263336181640625"),
        ]
        for literal, expected in cases:
            for sign in ("", "-"):
                with self.subTest(value=sign + literal):
                    self.assertEqual(repr(decimal.Decimal(float.fromhex(sign + literal))),
                                     f"Decimal('{sign}{expected}')")

    def test_triples(self):
        cases = [
            ((0, (1, 2, 3, 0), -2), "12.30"),
            ([1, [0, 0, 0], -3], "-0.000"),
            ((0, (), 2), "0E+2"),
            ((0, (0, 0, 1, 2), "n"), "NaN12"),
            ((1, (), "N"), "-sNaN"),
            ((1, (1, 2), "F"), "-Infinity"),
            (decimal.DecimalTuple(0, (1, 2, 0), -1), "12.0"),
            ((False, (True, False), 0), "10"),
        ]
        for value, expected in cases:
            with self.subTest(value=value):
                self.assertEqual(repr(decimal.Decimal(value)), f"Decimal('{expected}')")

    def test_decimal_copy_and_subclasses(self):
        class DecimalSubclass(decimal.Decimal):
            pass

        value = decimal.Decimal("-1.2000")
        self.assertIs(decimal.Decimal(value), value)
        sub = DecimalSubclass(value)
        self.assertIs(type(sub), DecimalSubclass)
        self.assertEqual(repr(sub), "Decimal('-1.2000')")
        copied = decimal.Decimal(sub)
        self.assertIs(type(copied), decimal.Decimal)
        self.assertIsNot(copied, sub)
        self.assertEqual(repr(copied), repr(value))
        self.assertEqual(repr(decimal.Decimal(value="1.20", context=None)), "Decimal('1.20')")

    def test_primitive_subclasses_do_not_coerce(self):
        class IntSubclass(int):
            def __int__(self):
                raise AssertionError

        class FloatSubclass(float):
            def __float__(self):
                raise AssertionError

        class StringSubclass(str):
            def __str__(self):
                raise AssertionError

        self.assertEqual(repr(decimal.Decimal(IntSubclass(42))), "Decimal('42')")
        self.assertEqual(repr(decimal.Decimal(FloatSubclass(1.5))), "Decimal('1.5')")
        self.assertEqual(repr(decimal.Decimal(StringSubclass("1.20"))), "Decimal('1.20')")

    def test_invalid_strings(self):
        for value in ("", " ", ".", "1.2.3", "1e", "1e+", "1 e2", "--1", "1\x00", "NaN1.2", "Infinity1", "\u00b21"):
            with self.subTest(value=value):
                with self.assertRaises(decimal.InvalidOperation) as caught:
                    decimal.Decimal(value)
                self.assertEqual(caught.exception.args, ([decimal.ConversionSyntax],))
        for value in ("1e1000000000000000000", "10e999999999999999999", "1e-1999999999999999998",
                      "0e-1999999999999999998", "0e999999999999999999999999", "1e-9223372036854775808"):
            with self.subTest(value=value):
                with self.assertRaises(decimal.InvalidOperation) as caught:
                    decimal.Decimal(value)
                self.assertEqual(caught.exception.args, ([decimal.InvalidOperation],))

    def test_invalid_triples(self):
        for value in ((0, (1,)), (0, (1,), 0, 0), (2, (1,), 0), (0.0, (1,), 0),
                      (0, (1,), 0.0), (0, (1,), "x"), (0, (10,), 0), (0, (1.0,), 0),
                      (0, "12", 0), (0, iter([1, 2]), 0)):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    decimal.Decimal(value)
        for value in ((2**100, (1,), 0), (0, (2**100,), 0), (0, (1,), 2**100)):
            with self.assertRaises(OverflowError):
                decimal.Decimal(value)

    def test_invalid_types_and_arguments(self):
        class IndexOnly:
            def __index__(self):
                return 1

        for value in (None, b"1", object(), IndexOnly()):
            with self.subTest(value=type(value)):
                with self.assertRaises(TypeError):
                    decimal.Decimal(value)
        with self.assertRaises(TypeError):
            decimal.Decimal("1", object())
        with self.assertRaises(TypeError):
            decimal.Decimal("1", None, None)
        with self.assertRaises(TypeError):
            decimal.Decimal(unknown=1)


class DecimalStringCreationTests(unittest.TestCase):
    def test_mixed_constructor_inputs(self):
        class StringSubclass(str):
            pass

        for value in ("1.20", 42, 10**30, True, 1.5, (0, (1, 2), -1), b"1", None, object(),
                      "1.20", StringSubclass("1.20"), "\u0661.\u0662", "".join(("1", ".20"))):
            with self.subTest(value=value):
                if value is None or isinstance(value, bytes) or type(value) is object:
                    with self.assertRaises(TypeError):
                        decimal.Decimal(value)
                else:
                    self.assertEqual(decimal.Decimal(value).as_tuple(), reference.Decimal(value).as_tuple())

    def test_compact_strings_and_fallback(self):
        values = (
            "0", "-0", "+0", "-0.000", "00012.3400", ".1", "1.", "-.0",
            "1e0", "1E+2", "-1.2300e-7", "0e-999999999999999", "1e9999999999999999",
            "99999999999999999", "999999999999999999", "9999999999999999999",
            "9223372036854775807", "9223372036854775808",
            "0." + "0" * 15, "0." + "0" * 16, "0." + "0" * 17,
            "1e999999999999999999", "1e-1999999999999999997",
            "  +12.300  ", "_1_._2_e_+_3_", "\u0661.\u0662", "\U0001d7d9.\U0001d7da",
            "Inf", "-Infinity", "NaN123", "-sNaN123",
        )

        class DecimalSubclass(decimal.Decimal):
            pass

        class StringSubclass(str):
            def __str__(self):
                raise AssertionError("string subclasses must not be coerced")

        with decimal.localcontext(prec=1, Emin=-1, Emax=1) as context:
            context.clear_flags()
            for value in values:
                expected = reference.Decimal(value).as_tuple()
                for cls in (decimal.Decimal, DecimalSubclass):
                    for text in (value, StringSubclass(value)):
                        with self.subTest(value=value, cls=cls, text_type=type(text)):
                            result = cls(text)
                            self.assertIs(type(result), cls)
                            self.assertEqual(result.as_tuple(), expected)
                            self.assertFalse(any(context.flags.values()))

    def test_random_short_strings(self):
        rng = random.Random(80152)
        for _ in range(300):
            digits = "".join(str(rng.randrange(10)) for _ in range(rng.randrange(1, 20)))
            point = rng.randrange(len(digits) + 1)
            value = rng.choice(("", "+", "-")) + digits[:point] + "." + digits[point:]
            if rng.randrange(2):
                value += rng.choice(("e", "E")) + rng.choice(("", "+", "-")) + str(rng.randrange(10**5))
            with self.subTest(value=value):
                self.assertEqual(decimal.Decimal(value).as_tuple(), reference.Decimal(value).as_tuple())

    def test_invalid_short_strings(self):
        for value in ("", "+", "-", ".", "+.", "1..0", "1e", "1e+", "1e-", "1e+-2",
                      "1e2.0", "1e2e3", "1e 2", "1x", "1\x00", "1+", "1-e2", "1.2.3"):
            with self.subTest(value=value):
                with decimal.localcontext() as context:
                    context.clear_traps()
                    context.clear_flags()
                    self.assertTrue(decimal.Decimal(value).is_qnan())
                    self.assertTrue(context.flags[decimal.InvalidOperation])
                    self.assertEqual(sum(context.flags.values()), 1)


class DecimalContextTests(unittest.TestCase):
    def setUp(self):
        self.original = decimal.getcontext()
        decimal.setcontext(decimal.Context())

    def tearDown(self):
        decimal.setcontext(self.original)

    def assertSameSettings(self, left, right):
        for name in ('prec', 'rounding', 'Emin', 'Emax', 'capitals', 'clamp', 'flags', 'traps'):
            self.assertEqual(getattr(left, name), getattr(right, name), name)

    def test_templates(self):
        default = decimal.DefaultContext
        self.assertEqual((default.prec, default.rounding, default.Emin, default.Emax,
                          default.capitals, default.clamp),
                         (28, decimal.ROUND_HALF_EVEN, -999999, 999999, 1, 0))
        self.assertEqual([s for s in SIGNALS if default.traps[s]],
                         [decimal.InvalidOperation, decimal.DivisionByZero, decimal.Overflow])
        self.assertFalse(any(default.flags.values()))
        basic = decimal.BasicContext
        self.assertEqual((basic.prec, basic.rounding), (9, decimal.ROUND_HALF_UP))
        self.assertEqual([s for s in SIGNALS if basic.traps[s]],
                         [decimal.InvalidOperation, decimal.DivisionByZero, decimal.Overflow,
                          decimal.Underflow, decimal.Clamped])
        extended = decimal.ExtendedContext
        self.assertEqual((extended.prec, extended.rounding), (9, decimal.ROUND_HALF_EVEN))
        self.assertFalse(any(extended.traps.values()))

    def test_constructor_and_reinitialization(self):
        ctx = decimal.Context(17, decimal.ROUND_DOWN, -50, 80, 0, 1,
                              [decimal.Inexact], [decimal.FloatOperation])
        self.assertEqual((ctx.prec, ctx.rounding, ctx.Emin, ctx.Emax, ctx.capitals, ctx.clamp),
                         (17, decimal.ROUND_DOWN, -50, 80, 0, 1))
        self.assertEqual([s for s in SIGNALS if ctx.flags[s]], [decimal.Inexact])
        self.assertEqual([s for s in SIGNALS if ctx.traps[s]], [decimal.FloatOperation])
        ctx.__init__(prec=None, rounding=None, Emin=None, Emax=None, capitals=None,
                     clamp=None, flags=None, traps=None)
        self.assertEqual(ctx.prec, 17)
        self.assertTrue(ctx.flags[decimal.Inexact])
        ctx.__init__(prec=12)
        self.assertEqual(ctx.prec, 12)
        self.assertEqual(ctx.capitals, 0)
        self.assertTrue(ctx.traps[decimal.FloatOperation])
        with self.assertRaises(TypeError):
            decimal.Context(1, 2, 3, 4, 5, 6, 7, 8, 9)
        with self.assertRaises(TypeError):
            decimal.Context(unknown=1)
        with self.assertRaises(TypeError):
            decimal.Context(12, prec=15)

    def test_default_context_inheritance(self):
        template = decimal.DefaultContext
        saved = template.copy()
        try:
            template.prec = 42
            template.capitals = 0
            template.flags[decimal.Rounded] = True
            template.traps[decimal.FloatOperation] = True
            ctx = decimal.Context()
            self.assertEqual(ctx.prec, 42)
            self.assertEqual(ctx.capitals, 1)
            self.assertTrue(ctx.flags[decimal.Rounded])
            self.assertTrue(ctx.traps[decimal.FloatOperation])
            self.assertIsNot(ctx.flags, template.flags)
            fresh = contextvars.Context().run(decimal.getcontext)
            self.assertEqual(fresh.prec, 42)
            self.assertEqual(fresh.capitals, 0)
            self.assertFalse(any(fresh.flags.values()))
        finally:
            template.__init__(*saved.__reduce__()[1])

    def test_attribute_ranges(self):
        ctx = decimal.Context()
        ranges = {'prec': (1, decimal.MAX_PREC), 'Emin': (decimal.MIN_EMIN, 0),
                  'Emax': (0, decimal.MAX_EMAX), 'capitals': (0, 1), 'clamp': (0, 1)}
        for name, (low, high) in ranges.items():
            for value in (low, high, True, False):
                if name == 'prec' and not value:
                    continue
                if name == 'Emin' and value is True:
                    continue
                setattr(ctx, name, value)
                self.assertEqual(getattr(ctx, name), value)
            old = getattr(ctx, name)
            for value in (low - 1, high + 1):
                with self.assertRaises(ValueError):
                    setattr(ctx, name, value)
                self.assertEqual(getattr(ctx, name), old)
            for value in (None, '1', 1.5, object()):
                with self.assertRaises(TypeError):
                    setattr(ctx, name, value)
            for value in (2 ** 100, -(2 ** 100)):
                with self.assertRaises(OverflowError):
                    setattr(ctx, name, value)
        with self.assertRaises(ValueError):
            ctx.clamp = 2 ** 32
        ctx.prec = decimal.MAX_PREC
        ctx.Emin = decimal.MIN_EMIN
        ctx.Emax = decimal.MAX_EMAX
        self.assertEqual(ctx.Etiny(), decimal.MIN_ETINY)
        self.assertEqual(ctx.Etop(), 1)

    def test_index_conversion(self):
        class Index:
            def __index__(self):
                return 23
        # PyLong_AsSsize_t requires an actual int, even when __index__ is available.
        with self.assertRaises(TypeError):
            decimal.Context(prec=Index())
        ctx = decimal.Context()
        with self.assertRaises(TypeError):
            ctx.prec = Index()

    def test_rounding(self):
        ctx = decimal.Context()
        for rounding in ROUNDINGS:
            ctx.rounding = ''.join([rounding[:4], rounding[4:]])
            self.assertEqual(ctx.rounding, rounding)
        for value in (None, 1, object(), 'ROUND_UNKNOWN'):
            with self.assertRaises(TypeError):
                ctx.rounding = value

    def test_repr_and_exponent_helpers(self):
        ctx = decimal.Context(prec=5, Emin=-10, Emax=15, flags=[decimal.Rounded, decimal.Clamped], traps=[])
        self.assertEqual(ctx.Etiny(), -14)
        self.assertEqual(ctx.Etop(), 11)
        self.assertEqual(repr(ctx.radix()), "Decimal('10')")
        self.assertEqual(repr(ctx), 'Context(prec=5, rounding=ROUND_HALF_EVEN, Emin=-10, Emax=15, '
                                   'capitals=1, clamp=0, flags=[Clamped, Rounded], traps=[])')

    def test_attribute_slots(self):
        ctx = decimal.Context()
        for name in ('prec', 'Emin', 'Emax', 'rounding', 'capitals', 'clamp', 'flags', 'traps', 'unknown'):
            with self.assertRaises(AttributeError):
                delattr(ctx, name)
        with self.assertRaises(AttributeError):
            ctx.unknown = 1
        with self.assertRaises(AttributeError):
            getattr(ctx, 'unknown')
        with self.assertRaises(TypeError):
            ctx.__getattribute__(1)
        with self.assertRaises(TypeError):
            ctx.__setattr__(1, 2)
        class Subclass(decimal.Context):
            pass
        sub = Subclass()
        sub.extra = 12
        self.assertEqual(sub.extra, 12)
        with self.assertRaises(AttributeError):
            del sub.extra

    def test_copy_and_pickle(self):
        ctx = decimal.Context(prec=13, rounding=decimal.ROUND_FLOOR, Emin=-70, Emax=30,
                              capitals=0, clamp=1, flags=[decimal.Inexact], traps=[decimal.Underflow])
        for cloned in (ctx.copy(), ctx.__copy__(), copy.copy(ctx), copy.deepcopy(ctx)):
            self.assertIsNot(cloned, ctx)
            self.assertIs(type(cloned), decimal.Context)
            self.assertSameSettings(cloned, ctx)
            self.assertIsNot(cloned.flags, ctx.flags)
            self.assertIsNot(cloned.traps, ctx.traps)
            cloned.clear_flags()
            self.assertTrue(ctx.flags[decimal.Inexact])
        for protocol in range(pickle.HIGHEST_PROTOCOL + 1):
            cloned = pickle.loads(pickle.dumps(ctx, protocol))
            self.assertSameSettings(cloned, ctx)
        class Subclass(decimal.Context):
            pass
        sub = Subclass(prec=41)
        self.assertIs(type(sub.copy()), decimal.Context)
        self.assertIs(sub.__reduce__()[0], Subclass)

    def test_get_and_set_context(self):
        ctx = decimal.Context(prec=16)
        self.assertIsNone(decimal.setcontext(ctx))
        self.assertIs(decimal.getcontext(), ctx)
        for value in (None, 1, object()):
            with self.assertRaises(TypeError):
                decimal.setcontext(value)
        for template in (decimal.DefaultContext, decimal.BasicContext, decimal.ExtendedContext):
            decimal.setcontext(template)
            current = decimal.getcontext()
            self.assertIsNot(current, template)
            self.assertSameSettings(current, template)

    def test_localcontext(self):
        outer = decimal.getcontext()
        outer.prec = 37
        outer.flags[decimal.Inexact] = True
        with decimal.localcontext() as local:
            self.assertIs(decimal.getcontext(), local)
            self.assertIsNot(local, outer)
            self.assertSameSettings(local, outer)
            local.prec = 12
            local.clear_flags()
            with decimal.localcontext(prec=19, rounding=decimal.ROUND_UP) as inner:
                self.assertEqual(inner.prec, 19)
                self.assertEqual(inner.rounding, decimal.ROUND_UP)
            self.assertIs(decimal.getcontext(), local)
        self.assertIs(decimal.getcontext(), outer)
        self.assertEqual(outer.prec, 37)
        self.assertTrue(outer.flags[decimal.Inexact])
        source = decimal.Context(prec=21)
        with decimal.localcontext(source, prec=14, capitals=0, flags=[], traps=[]) as local:
            self.assertEqual(local.prec, 14)
            self.assertEqual(local.capitals, 0)
            self.assertFalse(any(local.traps.values()))
        self.assertEqual(source.prec, 21)
        for kw in ({'unknown': 1}, {'prec': 0}, {'ctx': 1}):
            with self.assertRaises((TypeError, ValueError)):
                decimal.localcontext(**kw)
        self.assertIs(decimal.getcontext(), outer)

    def test_localcontext_restoration_after_exception(self):
        outer = decimal.getcontext()
        with self.assertRaisesRegex(RuntimeError, 'test'):
            with decimal.localcontext():
                raise RuntimeError('test')
        self.assertIs(decimal.getcontext(), outer)
        manager = decimal.localcontext()
        another = decimal.Context()
        decimal.setcontext(another)
        manager.__enter__()
        self.assertIsNone(manager.__exit__())
        # CPython captures the outer context at manager creation, not entry.
        self.assertIs(decimal.getcontext(), outer)

    def test_contextvar_isolation(self):
        outer = decimal.getcontext()
        def run():
            inner = decimal.getcontext()
            self.assertIsNot(inner, outer)
            inner.prec = 47
            return inner
        inner = contextvars.Context().run(run)
        self.assertEqual(inner.prec, 47)
        self.assertIs(decimal.getcontext(), outer)
        results = []
        def thread_run():
            results.append(decimal.getcontext())
        thread = threading.Thread(target=thread_run)
        thread.start()
        thread.join()
        self.assertIsNot(results[0], outer)

    def test_async_context_switching(self):
        outer = decimal.getcontext()
        async def run(precision):
            with decimal.localcontext(prec=precision) as local:
                await asyncio.sleep(0)
                self.assertIs(decimal.getcontext(), local)
                return local.prec
        async def main():
            return await asyncio.gather(run(17), run(29))
        self.assertEqual(asyncio.run(main()), [17, 29])
        self.assertIs(decimal.getcontext(), outer)

    def test_constructor_signals(self):
        ctx = decimal.Context(traps=[])
        value = decimal.Decimal('bad', context=ctx)
        self.assertEqual(repr(value), "Decimal('NaN')")
        self.assertTrue(ctx.flags[decimal.InvalidOperation])
        self.assertFalse(decimal.getcontext().flags[decimal.InvalidOperation])
        ctx.traps[decimal.InvalidOperation] = True
        ctx.clear_flags()
        with self.assertRaises(decimal.InvalidOperation) as caught:
            decimal.Decimal('bad', context=ctx)
        self.assertEqual(caught.exception.args, ([decimal.ConversionSyntax],))
        self.assertTrue(ctx.flags[decimal.InvalidOperation])
        ctx.clear_flags()
        with self.assertRaises(decimal.InvalidOperation) as caught:
            decimal.Decimal('1e99999999999999999999', context=ctx)
        self.assertEqual(caught.exception.args, ([decimal.InvalidOperation],))
        self.assertTrue(ctx.flags[decimal.InvalidOperation])
        ctx.clear_flags()
        decimal.Decimal('sNaN123', context=ctx)
        self.assertFalse(any(ctx.flags.values()))

    def test_float_operation(self):
        class FloatSubclass(float):
            pass
        ctx = decimal.Context()
        for value in (0.1, float('inf'), float('nan'), FloatSubclass(1.5)):
            ctx.clear_flags()
            decimal.Decimal(value, context=ctx)
            self.assertTrue(ctx.flags[decimal.FloatOperation])
            ctx.clear_flags()
            ctx.traps[decimal.FloatOperation] = True
            with self.assertRaises(decimal.FloatOperation) as caught:
                decimal.Decimal(value, context=ctx)
            self.assertEqual(caught.exception.args, ([decimal.FloatOperation],))
            self.assertTrue(ctx.flags[decimal.FloatOperation])
            ctx.traps[decimal.FloatOperation] = False
        decimal.Decimal(0.5)
        self.assertTrue(decimal.getcontext().flags[decimal.FloatOperation])

    def test_repr_capitals(self):
        ctx = decimal.getcontext()
        ctx.capitals = 0
        self.assertEqual(repr(decimal.Decimal('1E+10')), "Decimal('1e+10')")
        ctx.capitals = 1
        self.assertEqual(repr(decimal.Decimal('1E+10')), "Decimal('1E+10')")


class DecimalSignalDictTests(unittest.TestCase):
    def setUp(self):
        self.ctx = decimal.Context(flags=[], traps=[])

    def test_mapping(self):
        flags = self.ctx.flags
        self.assertIsInstance(flags, MutableMapping)
        self.assertEqual(len(flags), 9)
        self.assertEqual(tuple(flags), SIGNALS)
        self.assertEqual(flags.copy(), dict.fromkeys(SIGNALS, False))
        self.assertFalse(any(flags.values()))
        self.assertIs(flags, self.ctx.flags)
        flags[decimal.Inexact] = 17
        self.assertIs(flags[decimal.Inexact], True)
        flags[decimal.Inexact] = None
        self.assertIs(flags[decimal.Inexact], False)
        flags.update({decimal.Rounded: True, decimal.Clamped: True})
        self.assertTrue(flags[decimal.Rounded])
        self.assertTrue(flags[decimal.Clamped])
        self.assertEqual(flags, flags.copy())
        self.assertFalse(flags != flags.copy())
        self.assertNotEqual(flags, self.ctx.traps)
        self.assertNotEqual(flags, {decimal.Inexact: True})
        with self.assertRaises(TypeError):
            hash(flags)
        with self.assertRaises(TypeError):
            flags < flags.copy()
        self.ctx.clear_flags()
        self.assertFalse(any(flags.values()))
        traps = self.ctx.traps
        traps[decimal.Inexact] = True
        self.ctx.clear_traps()
        self.assertFalse(any(traps.values()))

    def test_repr(self):
        flags = self.ctx.flags
        expected = '{' + ', '.join(f"<class 'decimal.{s.__name__}'>:False" for s in SIGNALS) + '}'
        self.assertEqual(repr(flags), expected)

    def test_keys_and_deletion(self):
        class InvalidSubclass(decimal.InvalidOperation):
            pass
        for key in (None, 1, [], decimal.ConversionSyntax, InvalidSubclass):
            with self.assertRaises(KeyError):
                self.ctx.flags[key]
            with self.assertRaises(KeyError):
                self.ctx.flags[key] = True
        for key in (decimal.Inexact, object()):
            with self.assertRaisesRegex(ValueError, 'signal keys cannot be deleted'):
                del self.ctx.flags[key]

    def test_assignment_preserves_view(self):
        flags = self.ctx.flags
        traps = self.ctx.traps
        values = dict.fromkeys(SIGNALS, False)
        values[decimal.Inexact] = [1]
        self.ctx.flags = values
        self.assertIs(self.ctx.flags, flags)
        self.assertTrue(flags[decimal.Inexact])
        self.ctx.traps = flags
        self.assertIs(self.ctx.traps, traps)
        self.assertTrue(traps[decimal.Inexact])
        flags[decimal.Inexact] = False
        self.assertTrue(traps[decimal.Inexact])
        for invalid in ({}, {decimal.Inexact: True}, dict(values, extra=False)):
            with self.assertRaises(KeyError):
                self.ctx.flags = invalid
        for invalid in ([decimal.Inexact], (), None, 1):
            with self.assertRaises(TypeError):
                self.ctx.flags = invalid
        with self.assertRaises(KeyError):
            decimal.Context(flags=[decimal.ConversionSyntax])
        with self.assertRaises(TypeError):
            decimal.Context(flags=(decimal.Inexact,))

    def test_truth_errors_preserve_flags(self):
        class BadBool:
            def __bool__(self):
                raise RuntimeError('bool failed')
        flags = self.ctx.flags
        before = flags.copy()
        with self.assertRaisesRegex(RuntimeError, 'bool failed'):
            flags[decimal.Inexact] = BadBool()
        self.assertEqual(flags, before)
        values = before.copy()
        values[decimal.Inexact] = BadBool()
        with self.assertRaises(RuntimeError):
            self.ctx.flags = values
        self.assertEqual(flags, before)
        with self.assertRaises(RuntimeError):
            flags == values

    def test_ownership(self):
        flags = self.ctx.flags
        del self.ctx
        flags[decimal.Inexact] = True
        self.assertTrue(flags[decimal.Inexact])
        self.assertEqual(len(flags), 9)

    def test_unbound_signal_dict(self):
        cls = type(self.ctx.flags)
        invalid = cls()
        for operation in (lambda: len(invalid), lambda: iter(invalid), lambda: repr(invalid),
                          lambda: invalid.copy(), lambda: invalid[decimal.Inexact],
                          lambda: invalid == self.ctx.flags, lambda: self.ctx.flags == invalid):
            with self.assertRaisesRegex(ValueError, 'invalid signal dict'):
                operation()
        with self.assertRaisesRegex(ValueError, 'invalid signal dict'):
            invalid[decimal.Inexact] = True

    @unittest.skipUnless(sys.implementation.name == 'graalpy', 'CPython 3.13 dereferences an unbound SignalDict here')
    def test_unbound_signal_dict_assignment(self):
        invalid = type(self.ctx.flags)()
        with self.assertRaisesRegex(ValueError, 'invalid signal dict'):
            self.ctx.flags = invalid


class DecimalNumericSlotsTests(unittest.TestCase):
    binary = (operator.add, operator.sub, operator.mul, operator.truediv,
              operator.floordiv, operator.mod, divmod)

    @staticmethod
    def describe(value):
        if type(value) is tuple:
            return tuple(map(repr, value))
        return repr(value)

    def check_binary(self, left, right, operation, prec=7, rounding="ROUND_HALF_EVEN", clamp=0,
                     emin=-20, emax=20):
        results = []
        for module in (decimal, reference):
            with module.localcontext(prec=prec, rounding=rounding, Emin=emin, Emax=emax, clamp=clamp) as context:
                context.clear_traps()
                context.clear_flags()
                result = operation(module.Decimal(left), module.Decimal(right))
                if module is reference and operation is operator.floordiv:
                    # libmpdec finalizes the integer quotient; _pydecimal leaves it unfinalized.
                    result = result._fix(context)
                elif module is reference and operation is divmod:
                    result = (result[0]._fix(context), result[1])
                results.append((self.describe(result), enabled_flags(context)))
        self.assertEqual(results[0], results[1], (left, right, operation.__name__, prec, rounding, clamp, emin, emax))

    def test_special_values(self):
        values = ("0", "-0", "0.00", "-0E+9", "1", "-3.00", "Infinity", "-Infinity",
                  "NaN123456", "-NaN9", "sNaN54321", "-sNaN7")
        for operation in self.binary:
            for left in values:
                for right in values:
                    self.check_binary(left, right, operation, prec=3, clamp=1)

    def test_rounding_and_exponent_limits(self):
        pairs = (("1.2300", "0"), ("999.5", "0"), ("-999.5", "0"),
                 ("0.0009995", "0"), ("1E+999999999999999999", "1E-1999999999999999997"),
                 ("1", "1E-999999999999999999"), ("1", "-1E-999999999999999999"),
                 ("-1", "1E-999999999999999999"), ("1E-999999999999999999", "1"),
                 ("-2", "3"), ("-10", "3"), ("999", "0.1"), ("1E-8", "3"))
        for rounding in ROUNDINGS:
            for clamp in (0, 1):
                for left, right in pairs:
                    for operation in self.binary:
                        self.check_binary(left, right, operation, prec=3, rounding=rounding,
                                          clamp=clamp, emin=-3, emax=3)

    def test_random_arithmetic(self):
        rng = random.Random(80152)
        for i in range(300):
            left = f"{rng.randrange(-10**40, 10**40)}e{rng.randrange(-65, 15)}"
            right = f"{rng.randrange(-10**30, 10**30)}e{rng.randrange(-55, 15)}"
            for operation in self.binary:
                self.check_binary(left, right, operation, prec=rng.choice((1, 3, 7, 28, 50)),
                                  rounding=rng.choice(ROUNDINGS), clamp=i % 2)

    def test_zero_remainder_with_large_exponent(self):
        for exponent in (2147483648, decimal.MAX_EMAX, decimal.MIN_ETINY):
            for sign in ('', '-'):
                for right in ('1', '-3e-2', '7e+2', '9223372036854775808'):
                    for operation in (operator.floordiv, operator.mod, divmod):
                        self.check_binary(f'{sign}0e{exponent}', right, operation,
                                          prec=3, emin=-3, emax=3, clamp=1)

    def test_compact_context_finalization(self):
        pairs = (('123456789', '9'), ('999', '1'), ('9995e-4', '0'),
                 ('5000000000000000000e-19', '1'), ('9223372036854775807e-19', '1'),
                 ('10e-5', '1e-5'), ('9995e-7', '1e-7'), ('0e+20', '0e-20'))
        settings = ((9, -20, 20, 0), (1, 0, 0, 0), (3, -3, 3, 0),
                    (3, -3, 3, 1), (28, -5, 5, 1))
        for rounding in ROUNDINGS:
            for prec, emin, emax, clamp in settings:
                for a, b in pairs:
                    for left_sign, right_sign in (('', ''), ('-', ''), ('', '-'), ('-', '-')):
                        for operation in (operator.add, operator.sub, operator.mul):
                            self.check_binary(left_sign + a, right_sign + b, operation, prec=prec,
                                              rounding=rounding, emin=emin, emax=emax, clamp=clamp)

    def test_compact_division_and_remainder(self):
        pairs = (('123456789', '97'), ('100', '2'), ('100.00', '2'), ('12.3', '0.2'),
                 ('99999', '3'), ('9223372036854775807', '7'), ('1e-8', '3e-8'),
                 ('123e-8', '122e-8'), ('9e+18', '1'), ('1', '9e+18'))
        for rounding in ROUNDINGS:
            for prec, emin, emax, clamp in ((28, -999999, 999999, 0), (3, -3, 1, 1), (1, 0, 0, 0)):
                for a, b in pairs:
                    for left_sign, right_sign in (('', ''), ('-', ''), ('', '-'), ('-', '-')):
                        for operation in (operator.truediv, operator.floordiv, operator.mod, divmod):
                            self.check_binary(left_sign + a, right_sign + b, operation, prec=prec,
                                              rounding=rounding, emin=emin, emax=emax, clamp=clamp)

    def test_compact_divmod_combines_signals_before_trapping(self):
        with decimal.localcontext(prec=3, Emin=-3, Emax=1, clamp=1) as context:
            context.clear_traps()
            context.clear_flags()
            context.traps[decimal.Inexact] = True
            with self.assertRaises(decimal.Inexact):
                divmod(decimal.Decimal('123e-8'), decimal.Decimal('122e-8'))
            self.assertEqual(enabled_flags(context), ['Clamped', 'Inexact', 'Rounded', 'Subnormal', 'Underflow'])

    def test_exact_division_with_many_trailing_zeros(self):
        for prec in (1, 3, 28, 2048):
            for a, b in (('1', '2'), ('1.00', '40'), ('3', '6'), ('1', '1024')):
                self.check_binary(a, b, operator.truediv, prec=prec, emin=-5000, emax=5000)

    def test_coefficient_digit_boundaries(self):
        # Check both sides of decimal and binary magnitude boundaries, including the compact
        # coefficient limit and the small-power cache limit. Precision makes a one-digit error
        # observable in the rounded result or flags.
        for exponent in (1, 18, 19, 20, 28, 38, 63, 64, 126, 127, 128, 129, 256, 1024):
            power = 10**exponent
            binary_power = 1 << power.bit_length()
            for magnitude in (power - 1, power, power + 1, binary_power - 1, binary_power, binary_power + 1):
                for sign in (-1, 1):
                    self.check_binary(sign * magnitude, 1, operator.mul, prec=exponent, emin=-2000, emax=2000)
                    self.check_binary(sign * magnitude, 3, operator.truediv, prec=28, emin=-2000, emax=2000)

    def test_unary(self):
        for rounding in ROUNDINGS:
            for text in ("0", "-0.00", "1.2300", "-1.2300", "999.5", "-999.5", "1E-99",
                         "-1E-99", "Infinity", "-Infinity", "NaN123456", "-NaN9", "-sNaN12345"):
                for operation in (operator.pos, operator.neg, abs):
                    results = []
                    for module in (decimal, reference):
                        with module.localcontext(prec=3, rounding=rounding, Emin=-3, Emax=3, clamp=1) as context:
                            context.clear_traps()
                            context.clear_flags()
                            result = operation(module.Decimal(text))
                            results.append((repr(result), enabled_flags(context)))
                    self.assertEqual(results[0], results[1], (text, rounding, operation.__name__))

    def test_integer_operands_and_reflection(self):
        for integer in (False, True, -7, 0, 11, -(2**63), 2**63-1, 2**63, 10**80):
            for operation in self.binary + (operator.pow,):
                if operation is operator.pow and abs(integer) > 100:
                    continue
                for reverse in (False, True):
                    results = []
                    for module in (decimal, reference):
                        with module.localcontext() as context:
                            context.clear_traps()
                            context.clear_flags()
                            value = module.Decimal("3")
                            result = operation(integer, value) if reverse else operation(value, integer)
                            results.append((self.describe(result), enabled_flags(context)))
                    self.assertEqual(results[0], results[1], (integer, reverse, operation.__name__))

        class IntegerSubclass(int):
            def __int__(self):
                raise AssertionError("must use the stored integer value")

        self.assertEqual(repr(decimal.Decimal(2) + IntegerSubclass(3)), "Decimal('5')")

        class Reflected:
            def __radd__(self, other):
                return "reflected"

        self.assertEqual(decimal.Decimal(1) + Reflected(), "reflected")
        for operation in self.binary + (operator.pow,):
            for other in (1.0, "1", None, complex(1), object()):
                with self.assertRaises(TypeError):
                    operation(decimal.Decimal(1), other)
                with self.assertRaises(TypeError):
                    operation(other, decimal.Decimal(1))

    def test_base_type_results(self):
        class Subclass(decimal.Decimal):
            pass

        for operation in (operator.pos, operator.neg, abs):
            self.assertIs(type(operation(Subclass(2))), decimal.Decimal)
        for operation in self.binary + (operator.pow,):
            result = operation(Subclass(2), Subclass(3))
            for value in result if type(result) is tuple else (result,):
                self.assertIs(type(value), decimal.Decimal)

    def test_conversion_slots(self):
        for text in ("0", "-0", "0E+999999999999999999", "-0E-1999999999999999997", "1.99", "-1.99",
                     "9223372036854775807", "-9223372036854775808", "1E+100", "1E-999999999999999999"):
            with decimal.localcontext(prec=1, Emin=-1, Emax=1) as context:
                context.clear_flags()
                self.assertEqual(int(decimal.Decimal(text)), int(reference.Decimal(text)) if reference.Decimal(text) else 0)
                self.assertEqual(bool(decimal.Decimal(text)), bool(reference.Decimal(text)))
                self.assertEqual(enabled_flags(context), [])
        for text in ("0", "-0", "-0.00", "0E+999999999999999999", "0.1", "1.234567890123456789",
                     "1E+999999999999999999", "-1E-1999999999999999997", "5E-324", "2.4703282292062327E-324",
                     "1.7976931348623157E+308", "Infinity", "-Infinity", "NaN", "-NaN"):
            result = float(decimal.Decimal(text))
            expected = float(reference.Decimal(text))
            if math.isnan(expected):
                self.assertTrue(math.isnan(result))
            else:
                self.assertEqual(result, expected, text)
            self.assertEqual(math.copysign(1, result), math.copysign(1, expected), text)
        for text in ("Infinity", "-Infinity"):
            with self.assertRaises(OverflowError):
                int(decimal.Decimal(text))
        for text in ("NaN", "-NaN", "sNaN"):
            with self.assertRaises(ValueError):
                int(decimal.Decimal(text))
            self.assertTrue(bool(decimal.Decimal(text)))
        with self.assertRaises(ValueError):
            float(decimal.Decimal("sNaN"))

    def test_power(self):
        pairs = (("0", "0"), ("-0", "3"), ("0", "-3"), ("0", "Infinity"),
                 ("-2", "0.5"), ("-2", "Infinity"), ("Infinity", "0"), ("-Infinity", "-3"),
                 ("1.00", "Infinity"), ("1.00", "100"), ("1.00", "-3"), ("1", "0.5"),
                 ("2", "10"), ("2", "-3"), ("2", "0.5"), ("4", "0.5"),
                 ("32", "0.2"), ("0.25", "-0.5"), ("1E+10", "0.1"),
                 ("1.000001", "1000000"), ("10.0", "2"), ("2", "1000"),
                 ("2", "1E+999999999999999999"), ("0.9", "1E+999999999999999999"),
                 ("2", "1E-999999999999999999"), ("0.9", "1E-999999999999999999"),
                 ("NaN123", "sNaN45"), ("-sNaN67", "0"))
        for rounding in ROUNDINGS:
            for left, right in pairs:
                if (left, right) == ("1E+10", "0.1") or right == "1E-999999999999999999":
                    # The C implementation rounds ln, the product, and exp separately;
                    # its directed rounding can differ from _pydecimal at exact boundaries.
                    continue
                self.check_binary(left, right, operator.pow, prec=7, rounding=rounding)
        for left, right in (("1.23456789", "-123"), ("2", "0.5"), ("1.000000000001", "123456789")):
            self.check_binary(left, right, operator.pow, prec=100)

    def test_power_working_precision(self):
        # CPython _decimal results, including the intermediate rounding documented for power.
        for rounding in ROUNDINGS:
            with decimal.localcontext(prec=7, Emin=-20, Emax=20, rounding=rounding) as context:
                context.clear_flags()
                result = decimal.Decimal("1E+10") ** decimal.Decimal("0.1")
                expected = "9.999999" if rounding in ("ROUND_DOWN", "ROUND_FLOOR", "ROUND_05UP") else "10.00000"
                self.assertEqual(repr(result), f"Decimal('{expected}')")
                self.assertEqual(enabled_flags(context), ["Inexact", "Rounded"])
                for base in ("2", "0.9"):
                    context.clear_flags()
                    self.assertEqual(repr(decimal.Decimal(base) ** decimal.Decimal("1E-999999999999999999")),
                                     "Decimal('1.000000')")
                    self.assertEqual(enabled_flags(context), ["Inexact", "Rounded"])
        with decimal.localcontext(prec=10, Emin=-9, Emax=9) as context:
            context.clear_flags()
            self.assertEqual(repr(decimal.Decimal("1E-18") ** decimal.Decimal("0.5")), "Decimal('1.000000000E-9')")
            self.assertEqual(enabled_flags(context), ["Inexact", "Rounded", "Subnormal", "Underflow"])
        with decimal.localcontext(prec=50, Emin=-34, Emax=80, rounding=decimal.ROUND_CEILING, clamp=1) as context:
            context.clear_flags()
            self.assertEqual(repr(decimal.Decimal("8623471193863540274e8") ** -24), "Decimal('0E-83')")
            self.assertEqual(enabled_flags(context), ["Clamped", "Inexact", "Rounded", "Subnormal", "Underflow"])
        with decimal.localcontext(prec=100, Emin=-56, Emax=83, rounding=decimal.ROUND_05UP, clamp=1) as context:
            context.clear_traps()
            context.clear_flags()
            result = decimal.Decimal("-60644599447416923197e-8") ** 67
            self.assertEqual(enabled_flags(context), ["Inexact", "Overflow", "Rounded"])
            self.assertEqual(repr(result), "Decimal('-" + "9" * 84 + "." + "9" * 16 + "')")

    def test_max_precision_exact_division(self):
        cases = (("1", "1", "1"), ("1", "2", "0.5"), ("1.00", "2", "0.50"),
                 ("1", "40", "0.025"), ("3", "6", "0.5"), ("1", "1024", "0.0009765625"),
                 ("1E+100", "2", "5E+99"), ("-1", "2", "-0.5"), ("1", "-2", "-0.5"))
        for bounds in (dict(Emin=decimal.MIN_EMIN, Emax=decimal.MAX_EMAX), dict(clamp=1)):
            with decimal.localcontext(prec=decimal.MAX_PREC, **bounds) as context:
                context.clear_flags()
                for a, b, expected in cases:
                    a, b = decimal.Decimal(a), decimal.Decimal(b)
                    self.assertEqual(str(a / b), expected)
                    self.assertEqual(str(context.divide(a, b)), expected)
                if not bounds.get("clamp"):
                    self.assertEqual(str(decimal.Decimal(2) ** -1), "0.5")
                    self.assertEqual(str(decimal.Decimal(2) ** -10), "0.0009765625")
                self.assertEqual(enabled_flags(context), [])
                with self.assertRaises(MemoryError):
                    context.divide(decimal.Decimal(1), decimal.Decimal(3))
        with decimal.localcontext(prec=decimal.MAX_PREC, Emin=0, Emax=99) as context:
            # The bounded retry is accepted only when it raises no signals, even for
            # a terminating quotient that would be subnormal in the retry context.
            with self.assertRaises(MemoryError):
                context.divide(decimal.Decimal(1), decimal.Decimal(2))

    def test_power_of_one_clamping(self):
        # CPython's powers-of-one shortcut preserves the quantum without finalization.
        cases = (("1", "3", "1", []), ("-1", "3", "-1", []),
                 ("1.0", "1", "1.0", []), ("-1.0", "1", "-1.0", []),
                 ("1.0", "-1", "1", []), ("-1.0", "-1", "-1", []),
                 ("1.00", "2", "1.00", ["Rounded"]))
        for rounding in ROUNDINGS:
            with decimal.localcontext(prec=3, Emin=-9, Emax=1, clamp=1, rounding=rounding) as context:
                context.traps[decimal.Clamped] = True
                for a, b, expected, flags in cases:
                    a, b = decimal.Decimal(a), decimal.Decimal(b)
                    for operation in (operator.pow, context.power):
                        context.clear_flags()
                        self.assertEqual(str(operation(a, b)), expected)
                        self.assertEqual(enabled_flags(context), flags)

    def test_power_of_one_preferred_exponent(self):
        # libmpdec rounds the preferred digit count in the context before limiting it.
        for rounding in ROUNDINGS:
            with decimal.localcontext(prec=19, Emin=0, Emax=0, rounding=rounding) as context:
                context.clear_flags()
                a, b = decimal.Decimal("1.0"), decimal.Decimal("15.0")
                self.assertEqual(str(a ** b), "1." + "0" * 18)
                self.assertEqual(enabled_flags(context), ["Rounded"])
                context.traps[decimal.Rounded] = True
                context.clear_flags()
                with self.assertRaises(decimal.Rounded):
                    context.power(a, b)
                self.assertEqual(enabled_flags(context), ["Rounded"])

    def test_large_context_and_zero_exponents(self):
        with decimal.localcontext(prec=decimal.MAX_PREC, Emin=decimal.MIN_EMIN, Emax=decimal.MAX_EMAX) as context:
            context.clear_flags()
            for result in (+decimal.Decimal(1), decimal.Decimal(2) * 3, decimal.Decimal(3) ** 2,
                           decimal.Decimal("1.0") ** 2):
                self.assertIn(repr(result), ("Decimal('1')", "Decimal('6')", "Decimal('9')", "Decimal('1.00')"))
            self.assertEqual(enabled_flags(context), [])
        for exponent in (decimal.MAX_EMAX, decimal.MIN_ETINY):
            zero = decimal.Decimal((1, (0,), exponent))
            self.assertEqual(repr(decimal.Decimal(-2) ** zero), "Decimal('1')")
            self.assertEqual(repr(pow(decimal.Decimal(-2), zero, decimal.Decimal(5))), "Decimal('1')")
        with self.assertRaises(MemoryError):
            int(decimal.Decimal((0, (1,), decimal.MAX_EMAX)))
        with decimal.localcontext(Emax=decimal.MAX_EMAX, Emin=decimal.MIN_EMIN) as context:
            context.clear_traps()
            context.clear_flags()
            result = decimal.Decimal("1E+999999999999999999") ** decimal.Decimal("9.999")
            self.assertEqual(repr(result), "Decimal('Infinity')")
            self.assertEqual(enabled_flags(context), ["Inexact", "Overflow", "Rounded"])

    def test_modular_power(self):
        cases = (("-2", "3", "5"), ("-2", "4", "-5"), ("-5", "3", "5"),
                 ("2.00", "3.0", "5.000"), ("2", "1E+5", "123"),
                 ("1E+10000", "3", "7"), ("0", "0", "1"),
                 ("2", "-1", "5"), ("2", "0.5", "5"), ("2", "3", "0"),
                 ("2", "3", "12345"), ("Infinity", "2", "5"),
                 ("NaN123456", "sNaN45", "-sNaN67"), ("2", "NaN123456", "5"))
        for left, right, modulus in cases:
            results = []
            for module in (decimal, reference):
                with module.localcontext(prec=3) as context:
                    context.clear_traps()
                    context.clear_flags()
                    result = pow(module.Decimal(left), module.Decimal(right), module.Decimal(modulus))
                    results.append((repr(result), enabled_flags(context)))
            self.assertEqual(results[0], results[1], (left, right, modulus))
        self.assertEqual(repr(pow(decimal.Decimal(2), 3, 5)), "Decimal('3')")

    def test_sticky_flags_and_trap_precedence(self):
        with decimal.localcontext(prec=3, Emin=-3, Emax=3) as context:
            context.clear_flags()
            self.assertEqual(repr(+decimal.Decimal("1.2300")), "Decimal('1.23')")
            self.assertEqual(enabled_flags(context), ["Rounded"])
            self.assertEqual(repr(decimal.Decimal(1) / decimal.Decimal(3)), "Decimal('0.333')")
            self.assertEqual(enabled_flags(context), ["Inexact", "Rounded"])
            context.clear_flags()
            with self.assertRaises(decimal.Overflow):
                decimal.Decimal("9999") * 10
            self.assertEqual(enabled_flags(context), ["Inexact", "Overflow", "Rounded"])
            context.clear_flags()
            with self.assertRaises(decimal.InvalidOperation) as caught:
                divmod(decimal.Decimal(1), decimal.Decimal(0))
            self.assertEqual(caught.exception.args, ([decimal.InvalidOperation, decimal.DivisionByZero],))
            self.assertEqual(enabled_flags(context), ["DivisionByZero", "InvalidOperation"])
            context.clear_flags()
            with self.assertRaises(decimal.InvalidOperation) as caught:
                decimal.Decimal(0) / decimal.Decimal(0)
            self.assertEqual(caught.exception.args, ([decimal.DivisionUndefined],))
            context.clear_flags()
            context.traps[decimal.Underflow] = True
            with self.assertRaises(decimal.Underflow):
                decimal.Decimal("1E-9") / 3
            self.assertEqual(enabled_flags(context), ["Clamped", "Inexact", "Rounded", "Subnormal", "Underflow"])


class DecimalDunderTests(unittest.TestCase):
    def test_strings_and_attributes(self):
        for capitals in (0, 1):
            with decimal.localcontext(capitals=capitals):
                for text in ("1.2300", "-0E-9", "1E+99", "Infinity", "-NaN123", "sNaN9"):
                    value = decimal.Decimal(text)
                    expected = text.replace("E", "e") if not capitals else text
                    self.assertEqual(str(value), expected)
                    self.assertEqual(repr(value), f"Decimal('{expected}')")
                    self.assertIs(value.real, value)
                    self.assertEqual(repr(value.imag), "Decimal('0')")
                    self.assertIs(type(value.imag), decimal.Decimal)
                    self.assertEqual(value.__getattribute__("__class__"), decimal.Decimal)
                with self.assertRaises(AttributeError):
                    decimal.Decimal(1).missing
                with self.assertRaises(TypeError):
                    decimal.Decimal(1).__getattribute__(1)
        self.assertIsInstance(decimal.Decimal(1), numbers.Number)

    def test_copy_and_pickle(self):
        class Subclass(decimal.Decimal):
            @property
            def custom(self):
                return "descriptor"

        value = Subclass("-1.2300")
        self.assertEqual(value.custom, "descriptor")
        self.assertIs(value.__copy__(), value)
        self.assertIs(value.__deepcopy__({}), value)
        self.assertEqual(value.__reduce__(), (Subclass, ("-1.2300",)))
        for text in ("-0.00", "1E-9", "Infinity", "NaN123", "-sNaN9"):
            value = decimal.Decimal(text)
            self.assertIs(copy.copy(value), value)
            self.assertIs(copy.deepcopy(value), value)
            for protocol in range(pickle.HIGHEST_PROTOCOL + 1):
                self.assertEqual(repr(pickle.loads(pickle.dumps(value, protocol))), repr(value))
        self.assertGreater(decimal.Decimal(1).__sizeof__(), 0)
        self.assertGreater(decimal.Decimal("1" * 500).__sizeof__(), decimal.Decimal(1).__sizeof__())

    def test_hash(self):
        for text in ("0", "-0E+99", "1.00", "-1", "123456789012345678901234567890",
                     "0.1", "0.5", "1E-999999999999999999", "1E+999999999999999999",
                     "-1E-1999999999999999997", "Infinity", "-Infinity"):
            with decimal.localcontext(prec=1) as context:
                context.clear_flags()
                self.assertEqual(hash(decimal.Decimal(text)), hash(reference.Decimal(text)), text)
                self.assertFalse(any(context.flags.values()))
        for value in (0, -1, 2**100, Fraction(1, 2), 0.125, complex(2)):
            self.assertEqual(hash(decimal.Decimal(str(value.real) if isinstance(value, complex) else
                                                "0.5" if isinstance(value, Fraction) else str(value))), hash(value))
        nan = decimal.Decimal("NaN")
        self.assertEqual(hash(nan), object.__hash__(nan))
        self.assertEqual(hash(nan), hash(nan))
        with self.assertRaises(TypeError):
            hash(decimal.Decimal("sNaN"))

    def test_comparisons(self):
        operations = (operator.eq, operator.ne, operator.lt, operator.le, operator.gt, operator.ge)
        values = ("0", "-0", "-0.00", "1", "-2.50", "0.1", "1E-999999999999999999",
                  "1E+999999999999999999", "Infinity", "-Infinity", "NaN123", "sNaN9")
        for left in values:
            for right in values + (False, True, 10**100, 0.1, float("nan"), float("inf"), Fraction(1, 3), Fraction(-5, 2)):
                for operation in operations:
                    results = []
                    for module in (decimal, reference):
                        with module.localcontext(prec=1, Emin=-1, Emax=1) as context:
                            context.clear_traps()
                            context.clear_flags()
                            other = module.Decimal(right) if isinstance(right, str) else right
                            results.append((operation(module.Decimal(left), other), enabled_flags(context)))
                    self.assertEqual(results[0], results[1], (left, right, operation.__name__))
        value = decimal.Decimal("1")
        self.assertIs(value.__eq__("1"), NotImplemented)
        self.assertIs(value.__lt__(object()), NotImplemented)
        self.assertIs(value.__eq__(complex(1, 1)), NotImplemented)
        self.assertIs(value.__lt__(complex(1)), NotImplemented)
        with decimal.localcontext() as context:
            context.clear_flags()
            context.traps[decimal.FloatOperation] = True
            self.assertTrue(value == 1.0)
            self.assertTrue(value == complex(1))
            self.assertTrue(context.flags[decimal.FloatOperation])
            with self.assertRaises(decimal.FloatOperation):
                value < 2.0
            with self.assertRaises(decimal.InvalidOperation):
                decimal.Decimal("NaN") < 1
            with self.assertRaises(decimal.InvalidOperation):
                decimal.Decimal("sNaN") == 1
            self.assertFalse(decimal.Decimal("NaN") == 1)

    def test_integer_rounding(self):
        for text in ("0", "-0.00", "0E+999999999999999999", "2.5", "3.5", "-2.5", "-3.5",
                     "1.00001", "-1.00001", "1E-999999999999999999", "-1E-999999999999999999",
                     "123456789012345678901234567890.9", "1E+100"):
            for operation in (round, math.ceil, math.floor, math.trunc):
                with decimal.localcontext(prec=1, Emin=-1, Emax=1, rounding=decimal.ROUND_UP) as context:
                    context.clear_flags()
                    self.assertEqual(operation(decimal.Decimal(text)), operation(reference.Decimal(text)) if reference.Decimal(text) else 0, (text, operation))
                    self.assertIs(type(operation(decimal.Decimal(text))), int)
                    self.assertFalse(any(context.flags.values()))
        for text in ("Infinity", "-Infinity", "NaN", "sNaN"):
            for operation in (round, math.ceil, math.floor, math.trunc):
                with self.assertRaises(OverflowError if "Infinity" in text else ValueError):
                    operation(decimal.Decimal(text))

    def test_round_ndigits(self):
        for rounding in ROUNDINGS:
            for text in ("1.2300", "-0.0000", "0E+20", "0E-20", "999.99", "-1.2345", "1E-20",
                         "-1E-20", "Infinity", "NaN123456", "sNaN123456"):
                for ndigits in (-10, -3, -1, 0, 1, 4, 10, True, False, -(2**63), 2**63-1):
                    for clamp in (0, 1):
                        results = []
                        for module in (decimal, reference):
                            with module.localcontext(prec=5, Emin=-3, Emax=3, rounding=rounding, clamp=clamp) as context:
                                context.clear_traps()
                                context.clear_flags()
                                result = round(module.Decimal(text), ndigits)
                                results.append((repr(result), enabled_flags(context)))
                        self.assertEqual(results[0], results[1], (text, ndigits, rounding, clamp))
        for ndigits in (None, 1.0, "1", object()):
            with self.assertRaises(TypeError):
                decimal.Decimal(1).__round__(ndigits)
        with self.assertRaises(OverflowError):
            round(decimal.Decimal(1), 2**63)

    def test_complex(self):
        for text in ("0", "-0", "-0.00", "0.1", "1E+999999999999999999", "Infinity", "-Infinity", "NaN", "-NaN"):
            value = complex(decimal.Decimal(text))
            expected = complex(reference.Decimal(text))
            if math.isnan(expected.real):
                self.assertTrue(math.isnan(value.real))
            else:
                self.assertEqual(value, expected)
            self.assertEqual(math.copysign(1, value.real), math.copysign(1, expected.real))
            self.assertEqual(math.copysign(1, value.imag), 1)
        with self.assertRaises(ValueError):
            complex(decimal.Decimal("sNaN"))

    def test_format(self):
        specs = ("", "f", "F", "g", "G", "e", "E", "%", ".0f", ".3f", ".0g", ".4g", ".0e", ".3e",
                 "+20,.2f", "020,.2f", "^30", "*>30.4g", "😀^30.3f", "\x00<30", "=+20.4f", " 20.2%",
                 "#g", "#.0f", "z.0f", "_.3f", "n")
        for rounding in ROUNDINGS:
            for capitals in (0, 1):
                for text in ("0", "-0.00", "0E+9", "0E-9", "1.2300", "-123456789.999", "9.9999",
                             "-0.001", "1E-10", "1E+10", "Infinity", "NaN123", "-sNaN9"):
                    for spec in specs:
                        results = []
                        for module in (decimal, reference):
                            with module.localcontext(prec=1, Emin=-1, Emax=1, rounding=rounding, capitals=capitals) as context:
                                context.clear_flags()
                                results.append((format(module.Decimal(text), spec), enabled_flags(context)))
                        self.assertEqual(results[0], results[1], (text, spec, rounding, capitals))
        rng = random.Random(80152)
        for _ in range(300):
            text = f"{rng.randrange(-10**40, 10**40)}e{rng.randrange(-65, 15)}"
            spec = f"{rng.randrange(5, 50)}.{rng.randrange(0, 20)}{rng.choice('eEfFgG%')}"
            self.assertEqual(format(decimal.Decimal(text), spec), format(reference.Decimal(text), spec), (text, spec))

    def test_format_c_locale(self):
        saved_locale = locale.setlocale(locale.LC_NUMERIC)
        try:
            locale.setlocale(locale.LC_NUMERIC, "C")
            value = decimal.Decimal("1234567.89")
            for spec, expected in (("n", "1234567.89"), ("020n", "00000000001234567.89"),
                                   (".8n", "1234567.9"), (",f", "1,234,567.89")):
                self.assertEqual(format(value, spec), expected, spec)
            self.assertEqual(value.__format__("n", {"decimal_point": "@", "thousands_sep": "_", "grouping": "\3\2\0"}),
                             "12_34_567@89")
            self.assertEqual(value.__format__("n", {"thousands_sep": "_"}), "1234567.89")
            self.assertEqual(value.__format__("n", {"grouping": "\3\0"}), "1234567.89")
        finally:
            locale.setlocale(locale.LC_NUMERIC, saved_locale)

    def test_format_errors_and_overrides(self):
        value = decimal.Decimal("1234567.89")
        self.assertEqual(value.__format__("n", {"decimal_point": "@", "thousands_sep": "_", "grouping": "\3\2\0"}),
                         "12_34_567@89")
        self.assertEqual(value.__format__("f", {"decimal_point": "😀", "thousands_sep": "•", "grouping": "\3\0"}),
                         "1•234•567😀89")
        for spec in ("s", ".", "..2f", "<020", ",n", "++f", "2.3q", "#.00f", "z.00f"):
            with self.assertRaises(ValueError):
                format(value, spec)
        self.assertEqual(value.__format__("#f", None), "1234567.89")
        self.assertEqual(value.__format__("_f", None), "1_234_567.89")
        self.assertEqual(value.__format__("١٦.٢f", None), "      1234567.89")
        self.assertEqual(value.__format__(".٠٢f", None), "1234567.89")
        with self.assertRaises(TypeError):
            value.__format__(1)
        for override in (None, [], 1):
            with self.assertRaises(TypeError):
                value.__format__("f", override)
        for override in ({"decimal_point": ""}, {"decimal_point": "abcde"}, {"grouping": "\xff"}):
            with self.assertRaises(ValueError):
                value.__format__("f", override)
        with self.assertRaises(ValueError):
            format(value, f"{decimal.MAX_PREC + 1}f")

    def test_format_override_defaults(self):
        value = decimal.Decimal("1234567.89")
        for spec in ("", "f", "g", "e", "%"):
            for override in ({}, {"thousands_sep": "_"}, {"grouping": "\3\0"}):
                with self.assertRaises(ValueError):
                    value.__format__(spec, override)
        self.assertEqual(value.__format__("f", {"decimal_point": "@", "thousands_sep": "_"}), "1234567@89")
        self.assertEqual(value.__format__(",f", {}), "1,234,567.89")
        self.assertEqual(value.__format__(",f", {"thousands_sep": "_"}), "1_234_567.89")

    def test_rational_attribute_validation(self):
        class Index:
            def __index__(self):
                raise AssertionError("must not coerce rational attributes")

        class Rational:
            numerator = 1
            denominator = 2

        numbers.Rational.register(Rational)
        value = Rational()
        self.assertTrue(decimal.Decimal("0.5") == value)
        for invalid in (Index(), "1", 1.0):
            value.numerator = invalid
            with self.assertRaises(TypeError):
                decimal.Decimal(1) == value
            value.numerator = 1
            value.denominator = invalid
            with self.assertRaises(TypeError):
                decimal.Decimal(1) == value
            # Specials only need the numerator.
            self.assertTrue(decimal.Decimal("Infinity") > value)
            value.denominator = 2

    def test_format_deprecated_N(self):
        value = decimal.Decimal("1.234E+9")
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter("always", DeprecationWarning)
            self.assertEqual(format(value, "N"), format(value, "n").upper())
            self.assertEqual(len(caught), 1)
            self.assertIs(caught[0].category, DeprecationWarning)
            self.assertEqual(caught[0].filename, __file__)
            format(value, "N>20.3f")
            self.assertEqual(len(caught), 1)

    def test_compact_hash_exponents(self):
        rng = random.Random(80152)
        coefficients = (0, 1, 2**61 - 1, 2**61, 2**63 - 1)
        exponents = (-1999999999999999997, -999999999999999999, -100, -19, -1, 1, 18, 100, 999999999999999999)
        for coefficient in coefficients:
            for exponent in exponents:
                for sign in ("", "-"):
                    bounded_exponent = min(exponent, decimal.MAX_EMAX - len(str(coefficient)) + 1)
                    text = f"{sign}{coefficient}E{bounded_exponent}"
                    self.assertEqual(hash(decimal.Decimal(text)), hash(reference.Decimal(text)), text)
        for _ in range(500):
            text = f"{rng.randrange(-(2**63 - 1), 2**63)}E{rng.randrange(-1000, 1001)}"
            self.assertEqual(hash(decimal.Decimal(text)), hash(reference.Decimal(text)), text)

    def test_compact_comparisons(self):
        rng = random.Random(80152)
        operations = (operator.eq, operator.ne, operator.lt, operator.le, operator.gt, operator.ge)
        integers = (False, True, -1, 0, 1, -(2**31), 2**31, -(2**63), 2**63 - 1, 2**63, 10**100)
        values = ("0", "-0E+999999999999999999", "0E-1999999999999999997", "1.00", "-1.001",
                  "9223372036854775807", "922337203685477580E+1", "922337203685477581E+1",
                  "1E-1999999999999999997", "1E+999999999999999999")
        pairs = [(left, right) for left in values for right in values + integers]
        for _ in range(500):
            left = f"{rng.randrange(-(2**63 - 1), 2**63)}E{rng.randrange(-20, 21)}"
            right = f"{rng.randrange(-(2**63 - 1), 2**63)}E{rng.randrange(-20, 21)}"
            pairs.append((left, right))
        for left, right in pairs:
            for operation in operations:
                other = decimal.Decimal(right) if isinstance(right, str) else right
                expected = reference.Decimal(right) if isinstance(right, str) else right
                self.assertEqual(operation(decimal.Decimal(left), other), operation(reference.Decimal(left), expected),
                                 (left, right, operation.__name__))
                self.assertEqual(operation(other, decimal.Decimal(left)), operation(expected, reference.Decimal(left)),
                                 (right, left, operation.__name__))

    def test_compact_integer_rounding_boundaries(self):
        for coefficient in (0, 1, 4999999999999999999, 5000000000000000000, 5000000000000000001, 2**63 - 1):
            for exponent in (-999999999999999999, -20, -19, -18, -1, 0, 1, 18, 20):
                for sign in ("", "-"):
                    text = f"{sign}{coefficient}E{exponent}"
                    for operation in (round, math.floor, math.ceil):
                        self.assertEqual(operation(decimal.Decimal(text)), operation(reference.Decimal(text)), (text, operation))


class DecimalMethodTests(unittest.TestCase):
    unary = ("normalize", "to_integral", "to_integral_value", "to_integral_exact", "sqrt", "logb",
             "next_minus", "next_plus", "logical_invert", "is_normal", "is_subnormal", "number_class",
             "to_eng_string")
    binary = ("compare", "compare_signal", "compare_total", "compare_total_mag", "max", "min",
              "max_mag", "min_mag", "next_toward", "remainder_near", "copy_sign", "same_quantum",
              "logical_and", "logical_or", "logical_xor", "rotate", "shift", "scaleb")

    def check_method(self, name, *values, **settings):
        results = []
        for module in (decimal, reference):
            with module.localcontext(**settings) as context:
                context.clear_traps()
                context.clear_flags()
                operands = [module.Decimal(v) for v in values]
                try:
                    result = getattr(operands[0], name)(*operands[1:])
                    if isinstance(result, module.Decimal):
                        result = repr(result)
                    results.append((result, enabled_flags(context)))
                except (ValueError, OverflowError) as exc:
                    results.append((type(exc).__name__, enabled_flags(context)))
        self.assertEqual(results[0], results[1], (name, values, settings))

    def test_special_values(self):
        values = ("0", "0.00", "-0.00", "0E+7", "1", "1.00", "1.000", "-1", "-1.00", "Infinity", "-Infinity",
                  "NaN", "-NaN12345", "sNaN12345", "-sNaN9")
        for value in values:
            for name in self.unary:
                self.check_method(name, value, prec=3, Emin=-3, Emax=4, clamp=1)
            for other in values:
                for name in self.binary:
                    self.check_method(name, value, other, prec=3, Emin=-3, Emax=4, clamp=1)
                for third in ("-2", "Infinity", "-Infinity", "NaN98", "sNaN765"):
                    self.check_method("fma", value, other, third, prec=3, Emin=-3, Emax=4, clamp=1)

    def test_rounding_and_exponent_boundaries(self):
        values = ("-0E-100", "0E+100", "1.00000", "1.23", "-1.23000", "9.9999", "-9.9999",
                  "1.00001", "-1.00001", "0.999999", "-0.999999", "1E-3", "1E-8", "-1E-8",
                  "99999", "-99999", "1E+100", "-1E+100", "1.0001E-3", "9.9999E-4")
        for rounding in ROUNDINGS:
            for clamp in (0, 1):
                for value in values:
                    for name in self.unary:
                        self.check_method(name, value, prec=3, Emin=-3, Emax=4, rounding=rounding, clamp=clamp)
                    for other in ("0", "-0", "1E-8", "-1E-8", "1", "-1", "3.14", "1E+8"):
                        for name in ("next_toward", "remainder_near", "max", "min", "max_mag", "min_mag"):
                            self.check_method(name, value, other, prec=3, Emin=-3, Emax=4, rounding=rounding, clamp=clamp)

    def test_normalize_many_trailing_zeros(self):
        for coefficient in ('1', '16', '125', '999'):
            for zeros in (1, 3, 18, 128, 1024):
                value = coefficient + '0' * zeros + f'e-{zeros}'
                for clamp in (0, 1):
                    self.check_method('normalize', value, prec=zeros + 5, Emin=-2000, Emax=2000, clamp=clamp)
        for coefficient in ('2000', '1600', '2500', '8000'):
            self.check_method('normalize', coefficient + 'e-3', prec=5, Emin=-3, Emax=0, clamp=1)

    def test_digit_operations(self):
        for prec in (1, 3, 9, 28):
            for value in ("0", "-0", "1", "101", "1100101001101", "11111111111111111111111111111",
                          "2", "11.0", "-11", "123456789.00"):
                for name in ("logical_and", "logical_or", "logical_xor"):
                    for other in ("0", "1", "1010101010101", "2", "-0"):
                        self.check_method(name, value, other, prec=prec)
                for name in ("rotate", "shift", "scaleb"):
                    for count in (str(-prec-1), str(-prec), "-1", "0", "1", str(prec), str(prec+1),
                                  "1.0", "1E+1", "Infinity", "NaN"):
                        self.check_method(name, value, count, prec=prec)

    def test_random_arithmetic(self):
        rng = random.Random(80152)
        for i in range(500):
            operands = [f"{rng.randrange(-10**40, 10**40)}E{rng.randrange(-45, 25)}" for _ in range(3)]
            settings = dict(prec=rng.choice((1, 3, 5, 19, 28, 50)), Emin=-20, Emax=20,
                            rounding=rng.choice(ROUNDINGS), clamp=i % 2)
            for name in ("sqrt", "normalize", "next_minus", "next_plus", "to_integral_exact", "to_eng_string"):
                self.check_method(name, operands[0], **settings)
            for name in ("next_toward", "remainder_near", "max_mag", "min_mag"):
                self.check_method(name, *operands[:2], **settings)
            self.check_method("fma", *operands, **settings)

    def test_conversion_and_classification(self):
        values = ("0", "-0E-100", "0E+999999999999999999", "1.23000", "123456789123456789123456789",
                  "-100.00", "1E-30", "3.14E+30", "Infinity", "-Infinity", "NaN", "sNaN", "-NaN123")
        for value in values:
            for name in ("as_tuple", "as_integer_ratio", "adjusted", "is_canonical", "is_finite", "is_infinite",
                         "is_nan", "is_qnan", "is_snan", "is_signed", "is_zero", "copy_abs", "copy_negate", "radix"):
                self.check_method(name, value)
            obj = decimal.Decimal(value)
            self.assertIs(obj.canonical(), obj)
            self.assertIs(obj.conjugate(), obj)
        for value in (0, -0.0, 0.1, -1.25, 5e-324, float("inf"), float("nan"), 10**100, True):
            result = decimal.Decimal.from_float(value)
            expected = reference.Decimal.from_float(value)
            self.assertEqual(result.as_tuple(), expected.as_tuple())
            self.assertIs(type(result), decimal.Decimal)

    def test_exact_product_and_nearest_remainder(self):
        for prec in (1, 3, 9):
            for a, b, c in (("123456789", "123456789", "-15241578750190520"),
                            ("9.9999", "9.9999", "-99.998"), ("-0", "2", "-0.00")):
                self.check_method("fma", a, b, c, prec=prec)
            for a, b in (("1", "123456789123456789E-10"), ("3", "2"), ("5", "2"),
                         ("-3", "2"), ("-5", "-2"), ("999.5", "1"), ("998.5", "1")):
                self.check_method("remainder_near", a, b, prec=prec)
            for a, b in (("1E-3", "0"), ("-1E-3", "0"), ("0", "-1"), ("-0", "1")):
                self.check_method("next_toward", a, b, prec=prec, Emin=-3, Emax=3)

    def test_extreme_exponents(self):
        for value in ("1E+999999999999999999", "1E-999999999999999999", "0E-1999999999999999997",
                      "0E+999999999999999999", "-0E-999999999999999999"):
            for name in ("normalize", "sqrt", "logb", "next_minus", "next_plus", "to_integral_exact", "to_eng_string"):
                self.check_method(name, value, prec=3, Emin=-3, Emax=3)
            for name in ("next_toward", "scaleb", "shift", "rotate", "compare_total", "remainder_near"):
                self.check_method(name, value, "1", prec=3, Emin=-3, Emax=3)
        # A huge precision need not allocate a huge coefficient for small, exact results.
        context = decimal.Context(prec=decimal.MAX_PREC, Emin=decimal.MIN_EMIN, Emax=decimal.MAX_EMAX)
        for name in ("rotate", "shift"):
            self.assertEqual(getattr(decimal.Decimal("12.0"), name)(1, context=context).as_tuple(), (0, (1, 2, 0, 0), -1))
        self.assertTrue(decimal.Decimal(0).next_plus(context=context).is_finite())
        for value in ("1", "4.00", "2.25", "40000.00"):
            obj = decimal.Decimal(value)
            self.assertEqual(obj.sqrt(context=context) ** 2, obj)

    def test_context_arguments_and_traps(self):
        value = decimal.Decimal("1.2345")
        with decimal.localcontext(prec=1) as current:
            current.clear_flags()
            context = decimal.Context(prec=3, rounding=decimal.ROUND_DOWN)
            context.clear_traps()
            for name in self.unary:
                context.clear_flags()
                getattr(value, name)(context=context)
                self.assertFalse(any(current.flags.values()), name)
            for name in self.binary:
                context.clear_flags()
                getattr(value, name)(1, context=context)
                self.assertFalse(any(current.flags.values()), name)
            context.clear_flags()
            self.assertEqual(str(value.fma(1, 0, context=context)), "1.23")
            self.assertFalse(any(current.flags.values()))
            self.assertEqual(str(value.to_integral(rounding=decimal.ROUND_UP, context=context)), "2")
            self.assertEqual(context.rounding, decimal.ROUND_DOWN)
            context.traps[decimal.Inexact] = True
            context.clear_flags()
            with self.assertRaises(decimal.Inexact):
                value.to_integral_exact(context=context)
            self.assertEqual(enabled_flags(context), ["Inexact", "Rounded"])
            context.traps[decimal.InvalidOperation] = True
            with self.assertRaises(decimal.InvalidOperation):
                decimal.Decimal("sNaN").compare_total(1, context=context).compare_signal(decimal.Decimal("NaN"), context=context)

    def test_argument_validation_and_subclasses(self):
        class Subclass(decimal.Decimal):
            pass

        class FloatSubclass(decimal.Decimal):
            def __new__(cls, value):
                assert type(value) is decimal.Decimal
                return super().__new__(cls, value)

        self.assertIs(type(FloatSubclass.from_float(0.5)), FloatSubclass)
        value = Subclass("1.23")
        for name in self.unary:
            with self.assertRaises(TypeError):
                getattr(value, name)(context=1)
            with self.assertRaises(TypeError):
                getattr(value, name)(extra=1)
        for name in self.binary:
            for other in ("1", 1.0, None, object()):
                with self.assertRaises(TypeError):
                    getattr(value, name)(other)
            with decimal.localcontext() as context:
                context.clear_traps()
                result = getattr(value, name)(True)
            if isinstance(result, decimal.Decimal):
                self.assertIs(type(result), decimal.Decimal)
            with self.assertRaises(TypeError):
                getattr(value, name)(1, context=1)
            with self.assertRaises(TypeError):
                getattr(value, name)(1, extra=1)
        for name in ("to_integral", "to_integral_value", "to_integral_exact"):
            for rounding in ("bad", 1, object()):
                with self.assertRaises(TypeError):
                    getattr(value, name)(rounding=rounding)
        for operands in (("1", 1), (1, "1"), (1.0, 1), (1, None)):
            with self.assertRaises(TypeError):
                decimal.Decimal(1).fma(*operands)
        with decimal.localcontext() as context:
            context.clear_flags()
            context.traps[decimal.FloatOperation] = True
            decimal.Decimal.from_float(0.1)
            self.assertFalse(any(context.flags.values()))
        for value in ("1", decimal.Decimal(1), None):
            with self.assertRaises(TypeError):
                decimal.Decimal.from_float(value)


class DecimalContextMethodTests(unittest.TestCase):
    unary = ("abs", "exp", "ln", "log10", "minus", "next_minus", "next_plus", "normalize", "plus",
             "to_integral", "to_integral_exact", "to_integral_value", "sqrt", "is_finite", "is_infinite",
             "is_nan", "is_normal", "is_qnan", "is_signed", "is_snan", "is_subnormal", "is_zero",
             "copy_abs", "copy_decimal", "copy_negate", "logb", "logical_invert", "number_class",
             "to_sci_string", "to_eng_string")
    binary = ("add", "compare", "compare_signal", "divide", "divide_int", "divmod", "max", "max_mag",
              "min", "min_mag", "multiply", "next_toward", "quantize", "remainder", "remainder_near",
              "subtract", "power", "compare_total", "compare_total_mag", "copy_sign", "logical_and",
              "logical_or", "logical_xor", "rotate", "same_quantum", "scaleb", "shift")

    @staticmethod
    def snapshot(module, value):
        if isinstance(value, module.Decimal):
            return value.as_tuple()
        if isinstance(value, tuple):
            return tuple(DecimalContextMethodTests.snapshot(module, item) for item in value)
        return value

    def check_operation(self, name, *values, **settings):
        results = []
        for module in (decimal, reference):
            # The receiver must control rounding and status independently of the active context.
            with module.localcontext(prec=19, Emin=-99, Emax=99) as active:
                active.clear_flags()
                context = module.Context(traps=[], **settings)
                operands = [module.Decimal(value) if isinstance(value, str) else value for value in values]
                try:
                    result = getattr(context, name)(*operands)
                    results.append((self.snapshot(module, result), enabled_flags(context)))
                except module.DecimalException as exc:
                    results.append((type(exc).__name__, enabled_flags(context)))
                self.assertFalse(any(active.flags.values()), (name, values))
        self.assertEqual(results[0], results[1], (name, values, settings))

    def test_operations_use_receiver_context(self):
        settings = dict(prec=3, Emin=-3, Emax=4, clamp=1)
        values = ("-0.00", "1.2345", "-12.50", "1E-5", "1E+7", "Infinity", "-Infinity", "NaN12345", "sNaN9")
        for name in self.unary:
            for value in values:
                self.check_operation(name, value, **settings)
        for name in self.binary:
            for left in values:
                for right in ("0", "-1.00", "2.1", "Infinity", "NaN7", "sNaN8"):
                    self.check_operation(name, left, right, **settings)
        for values in (("1.234", "5.678", "-7.007"), ("0", "Infinity", "NaN7"),
                       ("NaN7", "sNaN8", "sNaN9"), ("1E+7", "1", "-1E+7")):
            self.check_operation("fma", *values, **settings)

    def test_rounding_modes(self):
        for rounding in ROUNDINGS:
            for name in ("abs", "minus", "plus", "normalize", "to_integral", "to_integral_exact", "to_integral_value"):
                self.check_operation(name, "-1.250", prec=2, rounding=rounding)
                self.check_operation(name, "-0.00", prec=2, rounding=rounding)
            for name in ("add", "subtract", "multiply", "divide", "quantize", "remainder", "remainder_near"):
                self.check_operation(name, "1.250", "1", prec=2, rounding=rounding)

    def test_exact_integer_operands(self):
        for name in self.unary:
            for value in (True, False, -10, 101, 2**80):
                self.check_operation(name, value, prec=7)
        for name in self.binary:
            self.check_operation(name, 10, 3, prec=7)
            self.check_operation(name, "10", 3, prec=7)
            self.check_operation(name, 10, "3", prec=7)
        self.check_operation("fma", 2, 3, 4, prec=2)
        self.check_operation("power", 2, 8, 5, prec=2)

    def test_invalid_operand_types(self):
        context = decimal.Context(traps=[])
        for name in self.unary + self.binary + ("fma",):
            count = 1 if name in self.unary else 3 if name == "fma" else 2
            for position in range(count):
                for invalid in ("1", 1.0, None, (), object()):
                    operands = [decimal.Decimal(1)] * count
                    operands[position] = invalid
                    with self.assertRaises(TypeError, msg=(name, position, invalid)):
                        getattr(context, name)(*operands)
            with self.assertRaises(TypeError, msg=name):
                getattr(context, name)(*[1] * (count - 1))
            if name != "power":
                with self.assertRaises(TypeError, msg=name):
                    getattr(context, name)(*[1] * (count + 1))
                with self.assertRaises(TypeError, msg=name):
                    getattr(context, name)(a=1)
        self.assertFalse(any(context.flags.values()))

    def test_canonical_and_identity(self):
        class DecimalSubclass(decimal.Decimal):
            pass
        context = decimal.Context(prec=1, traps=[])
        for operand in (decimal.Decimal("-0.00"), decimal.Decimal("sNaN1234"), DecimalSubclass("1.234")):
            self.assertIs(context.canonical(operand), operand)
            self.assertIs(context.copy_decimal(operand), operand)
            self.assertIs(context.is_canonical(operand), True)
        for name in ("canonical", "is_canonical"):
            for invalid in (1, "1", None):
                with self.assertRaisesRegex(TypeError, "argument must be a Decimal"):
                    getattr(context, name)(invalid)
        self.assertFalse(any(context.flags.values()))

    def test_quiet_operations_preserve_representation(self):
        context = decimal.Context(prec=1, Emin=-1, Emax=1, traps=[decimal.InvalidOperation])
        value = decimal.Decimal("-sNaN1234")
        self.assertEqual(context.copy_abs(value).as_tuple(), decimal.Decimal("sNaN1234").as_tuple())
        self.assertEqual(context.copy_negate(value).as_tuple(), decimal.Decimal("sNaN1234").as_tuple())
        self.assertEqual(context.copy_sign(value, 1).as_tuple(), decimal.Decimal("sNaN1234").as_tuple())
        self.assertEqual(context.compare_total(value, value), 0)
        self.assertEqual(context.compare_total_mag(value, value), 0)
        self.assertTrue(context.same_quantum(value, decimal.Decimal("NaN")))
        self.assertFalse(any(context.flags.values()))

    def test_power_keywords(self):
        context = decimal.Context(prec=3)
        self.assertEqual(context.power(a=2, b=8, modulo=5), 1)
        self.assertEqual(context.power(2, b=8, modulo=None), 256)
        self.assertEqual(context.power(2, 8), 256)
        for kwargs in ({"a": 2, "b": 8, "unknown": 1}, {"a": 2}, {"b": 8}):
            with self.assertRaises(TypeError):
                context.power(**kwargs)
        with self.assertRaises(TypeError):
            context.power(2, 8, a=2)
        with self.assertRaises(TypeError):
            context.power(2, 8, 5, 1)
        with self.assertRaises(TypeError):
            context.power(2, 8, "5")

    def test_traps_and_flag_ownership(self):
        with decimal.localcontext(prec=19) as active:
            active.clear_flags()
            context = decimal.Context(prec=2, traps=[decimal.Inexact])
            with self.assertRaises(decimal.Inexact):
                context.add(decimal.Decimal("1.23"), 0)
            self.assertEqual(enabled_flags(context), ["Inexact", "Rounded"])
            self.assertFalse(any(active.flags.values()))
            context.clear_flags()
            context.traps[decimal.DivisionByZero] = True
            with self.assertRaises(decimal.DivisionByZero):
                context.divide(1, 0)
            self.assertEqual(enabled_flags(context), ["DivisionByZero"])
            context.clear_flags()
            context.traps[decimal.InvalidOperation] = True
            with self.assertRaises(decimal.InvalidOperation) as caught:
                context.divmod(0, 0)
            self.assertEqual(caught.exception.args, ([decimal.DivisionUndefined],))
            self.assertTrue(context.flags[decimal.InvalidOperation])
            self.assertFalse(any(active.flags.values()))

    def test_apply_preserves_sign_and_signaling_nan(self):
        context = decimal.Context(prec=2, clamp=1, traps=[decimal.InvalidOperation])
        for value, expected in (("-0.00", "-0.00"), ("sNaN123", "sNaN3"), ("-NaN123", "-NaN3"),
                                ("1.234", "1.2")):
            context.clear_flags()
            self.assertEqual(context._apply(decimal.Decimal(value)).as_tuple(), decimal.Decimal(expected).as_tuple())
            self.assertFalse(context.flags[decimal.InvalidOperation])
        self.assertEqual(context._apply(12), 12)
        with self.assertRaises(TypeError):
            context._apply("12")

    def test_create_decimal(self):
        context = decimal.Context(prec=3, traps=[])
        self.assertEqual(context.create_decimal(), 0)
        for value in (12345, True, "1.2345", decimal.Decimal("1.2345"), (1, (1, 2, 3, 4, 5), -4),
                      [1, (1, 2, 3, 4, 5), -4], 2**80):
            context.clear_flags()
            result = context.create_decimal(value)
            self.assertIs(type(result), decimal.Decimal)
            self.assertEqual(len(result.as_tuple().digits) <= 3, True)
        for value in ("-0.00", "sNaN12", "-NaN12", "Infinity", "-Infinity"):
            context.clear_flags()
            self.assertEqual(context.create_decimal(value).as_tuple(), decimal.Decimal(value).as_tuple())
            self.assertFalse(any(context.flags.values()))
        self.assertEqual(context.create_decimal("١.٢٣٤"), decimal.Decimal("1.23"))
        for invalid in (" 1", "1 ", "1\n", "\u00a01", "1\u2003", "1_2", "NaN_1", "bad", "NaN1234",
                        decimal.Decimal("sNaN1234"), (1, (1, 2, 3, 4), "n")):
            context.clear_flags()
            self.assertTrue(context.create_decimal(invalid).is_qnan())
            self.assertEqual(enabled_flags(context), ["InvalidOperation"])
            context.traps[decimal.InvalidOperation] = True
            with self.assertRaises(decimal.InvalidOperation) as caught:
                context.create_decimal(invalid)
            self.assertEqual(caught.exception.args, ([decimal.ConversionSyntax],))
            context.traps[decimal.InvalidOperation] = False
        for invalid in (None, object()):
            with self.assertRaises(TypeError):
                context.create_decimal(invalid)
        with self.assertRaises(ValueError):
            context.create_decimal((2, (1,), 0))

    def test_create_decimal_bounds(self):
        for clamp in (0, 1):
            for value in ("-0E-99", "0E+99", "1.2345", "1E-9", "9.999E+9", "-1E+9"):
                for module in (decimal, reference):
                    context = module.Context(prec=3, Emin=-3, Emax=4, clamp=clamp, traps=[])
                    result = context.create_decimal(value)
                    snapshot = (result.as_tuple(), enabled_flags(context))
                    if module is decimal:
                        expected = snapshot
                    else:
                        self.assertEqual(expected, snapshot, (value, clamp))

    def test_float_construction(self):
        context = decimal.Context(prec=5, traps=[])
        for value in (0.1, -0.0, float("inf"), float("-inf"), float("nan"), 1.5):
            context.clear_flags()
            explicit = context.create_decimal_from_float(value)
            self.assertFalse(context.flags[decimal.FloatOperation])
            explicit_flags = enabled_flags(context)
            context.clear_flags()
            implicit = context.create_decimal(value)
            self.assertEqual(explicit.as_tuple(), implicit.as_tuple())
            self.assertEqual(enabled_flags(context), sorted(explicit_flags + ["FloatOperation"]))
        for value in (True, 123456, 2**80):
            context.clear_flags()
            explicit = context.create_decimal_from_float(value)
            explicit_flags = enabled_flags(context)
            context.clear_flags()
            self.assertEqual(context.create_decimal(value), explicit)
            self.assertEqual(enabled_flags(context), explicit_flags)
        for value in ("1", decimal.Decimal(1), None):
            with self.assertRaisesRegex(TypeError, "argument must be int or float"):
                context.create_decimal_from_float(value)
        context.traps[decimal.FloatOperation] = True
        with self.assertRaises(decimal.FloatOperation):
            context.create_decimal(0.1)
        context.clear_flags()
        context.create_decimal_from_float(0.1)
        self.assertFalse(context.flags[decimal.FloatOperation])
        context.traps[decimal.Inexact] = True
        with self.assertRaises(decimal.Inexact):
            context.create_decimal_from_float(0.1)

    def test_strings_use_receiver_capitals(self):
        with decimal.localcontext(capitals=1):
            context = decimal.Context(capitals=0)
            value = decimal.Decimal("1E+10")
            self.assertEqual(context.to_sci_string(value), "1e+10")
            self.assertEqual(context.to_eng_string(value), "10e+9")
            self.assertFalse(any(context.flags.values()))


class DecimalQuantizeTests(unittest.TestCase):
    def check_quantize(self, text, quantum, override=None, **settings):
        results = []
        for module in (decimal, reference):
            with module.localcontext(**settings) as context:
                context.clear_traps()
                context.clear_flags()
                other = module.Decimal(quantum) if isinstance(quantum, str) else quantum
                result = module.Decimal(text).quantize(other, rounding=override)
                results.append((repr(result), enabled_flags(context)))
        self.assertEqual(results[0], results[1], (text, quantum, override, settings))

    def test_special_values(self):
        values = ("0", "-0.00", "2.5", "Infinity", "-Infinity", "NaN123456", "-NaN123",
                  "sNaN54321", "-sNaN9")
        for left in values:
            for right in values:
                self.check_quantize(left, right, prec=3, clamp=1)

    def test_compact_rounding_and_limits(self):
        values = ("0", "-0.0000", "1.2300", "-9.995", "5.00001", "-0.5000", "99.99", "-99.99",
                  "9223372036854775807", "9223372036854775800", "922337203685477581E-1",
                  "5000000000000000000E-19", "5000000000000000001E-19",
                  "4999999999999999999E-19", "9223372036854775807E-20",
                  "1E-999999999999999999", "0E+999999999999999999",
                  "0E-1999999999999999997", "1E+999999999999999999")
        for rounding in ROUNDINGS:
            for clamp in (0, 1):
                for text in values:
                    for exp in (-9, -6, -3, -1, 0, 1, 3, 5):
                        self.check_quantize(text, f"1E{exp:+}", prec=5, Emin=-3, Emax=5,
                                            rounding=rounding, clamp=clamp)
        for exp in (decimal.MAX_EMAX, decimal.MIN_ETINY):
            self.check_quantize("0", f"1E{exp:+}", prec=decimal.MAX_PREC,
                                Emin=decimal.MIN_EMIN, Emax=decimal.MAX_EMAX)

    def test_scaling_and_rounding_overflow(self):
        for rounding in ROUNDINGS:
            for text, quantum in (("9223372036854775807", "1E-1"), ("922337203685477581", "1E-1"),
                                  ("922337203685477580", "1E-1"), ("9999999999999999999", "1E+1"),
                                  ("999999999999999999", "1E+17"), ("9223372036854775807E+20", "1E+18"),
                                  ("999.9", "1")):
                for prec in (3, 19, 20, 25):
                    self.check_quantize(text, quantum, prec=prec, Emin=-30, Emax=30, rounding=rounding)

    def test_random_quantize(self):
        rng = random.Random(80152)
        for i in range(1500):
            limit = 2**63 if i % 2 else 10**40
            text = f"{rng.randrange(-limit, limit)}E{rng.randrange(-45, 25)}"
            quantum = f"1E{rng.randrange(-45, 25)}"
            self.check_quantize(text, quantum, prec=rng.choice((1, 3, 5, 19, 28, 50)),
                                rounding=rng.choice(ROUNDINGS), clamp=i % 2, Emin=-20, Emax=20)

    def test_explicit_context_and_rounding(self):
        with decimal.localcontext(prec=1, rounding=decimal.ROUND_DOWN) as current:
            current.clear_flags()
            context = decimal.Context(prec=10, rounding=decimal.ROUND_FLOOR)
            context.clear_flags()
            value = decimal.Decimal("1.234")
            self.assertEqual(str(value.quantize(decimal.Decimal("0.01"), decimal.ROUND_UP, context)), "1.24")
            self.assertEqual(context.rounding, decimal.ROUND_FLOOR)
            self.assertEqual(enabled_flags(context), ["Inexact", "Rounded"])
            self.assertFalse(any(current.flags.values()))
            self.assertEqual(str(value.quantize(exp=decimal.Decimal("0.01"), rounding=None, context=context)), "1.23")
            context.traps[decimal.Inexact] = True
            with self.assertRaises(decimal.Inexact):
                value.quantize(decimal.Decimal("0.01"), context=context)
        with decimal.localcontext(prec=2, Emin=-3, Emax=3) as context:
            context.clear_flags()
            with self.assertRaises(decimal.InvalidOperation):
                decimal.Decimal("999.9").quantize(decimal.Decimal(1))
            self.assertEqual(enabled_flags(context), ["InvalidOperation"])
            context.clear_flags()
            result = decimal.Decimal("1E-6").quantize(decimal.Decimal("1E-4"))
            self.assertEqual(str(result), "0.0000")
            self.assertEqual(enabled_flags(context), ["Inexact", "Rounded"])

    def test_conversion_and_subclasses(self):
        class Integer(int):
            def __int__(self):
                raise AssertionError("must use stored integer")

        class Subclass(decimal.Decimal):
            pass

        for exp in (False, True, 0, 10**100, Integer(1)):
            self.check_quantize("12.34", exp)
        result = Subclass("1.23").quantize(Subclass("0.1"))
        self.assertIs(type(result), decimal.Decimal)
        self.assertEqual(str(result), "1.2")
        value = decimal.Decimal("1.23")
        for exp in ("0.1", 0.1, None, object()):
            with self.assertRaises(TypeError):
                value.quantize(exp)
        for rounding in ("bad", 1, object()):
            with self.assertRaises(TypeError):
                value.quantize(decimal.Decimal("0.1"), rounding=rounding)
        with self.assertRaises(TypeError):
            value.quantize(decimal.Decimal("0.1"), context=1)
        with self.assertRaises(TypeError):
            value.quantize(decimal.Decimal("0.1"), extra=True)


class DecimalTranscendentalTests(unittest.TestCase):
    methods = ("exp", "ln", "log10")

    def check_method(self, name, value, **settings):
        results = []
        for module in (decimal, reference):
            context = module.Context(**settings)
            context.clear_traps()
            operand = module.Decimal(value)
            result = getattr(operand, name)(context=context)
            results.append((result.as_tuple(), enabled_flags(context)))
        self.assertEqual(results[0], results[1], (name, value, settings))

    def test_special_values_and_exact_results(self):
        values = ("0", "-0E-100", "0E+100", "1", "1.00000", "-1", "Infinity", "-Infinity",
                  "NaN", "-NaN12345", "sNaN12345", "-sNaN9", "10", "1E-100", "1E+100", "100.00")
        for value in values:
            for name in self.methods:
                for clamp in (0, 1):
                    self.check_method(name, value, prec=3, Emin=-3, Emax=4, clamp=clamp)

    def test_rounding_and_exponent_boundaries(self):
        values = ("1.23", "-1.23", "9.9999", "-9.9999", "1.00001", "0.999999", "1E-3",
                  "1E-8", "-1E-8", "99999", "-99999", "1E+100", "-1E+100", "1.0001E-3",
                  "9.9999E-4", "0.5", "-10", "23.02585", "-11.512925", "1E+25", "1E-25")
        for rounding in ROUNDINGS:
            for clamp in (0, 1):
                for value in values:
                    for name in self.methods:
                        self.check_method(name, value, prec=3, Emin=-3, Emax=4, rounding=rounding, clamp=clamp)
        for name in self.methods:
            for value in ("1.23456789", "0.9999", "1E+25", "1E-25"):
                self.check_method(name, value, prec=1, Emin=0, Emax=0)

    def test_high_precision_and_arguments_close_to_one(self):
        for prec in (1, 9, 28, 50, 100):
            for value in ("2", "0.5", "1.00000000000000000000000000000000000000000000000001",
                          "0.99999999999999999999999999999999999999999999999999", "3.141592653589793"):
                for name in self.methods:
                    self.check_method(name, value, prec=prec)
        # Huge exponents must not require constructing enormous powers of ten.
        for value in ("1E+999999999999999999", "-1E+999999999999999999",
                      "1E-999999999999999999", "-1E-999999999999999999"):
            for name in self.methods:
                self.check_method(name, value, prec=9)

    def test_values_near_rounding_midpoints(self):
        context = reference.Context(prec=100)
        for target in ("1.234565", "9.999995", "0.00001234565"):
            midpoint = reference.Decimal(target)
            arguments = {
                "exp": midpoint.ln(context=context),
                "ln": midpoint.exp(context=context),
                "log10": context.power(10, midpoint),
            }
            for name, argument in arguments.items():
                self.check_method(name, str(argument), prec=6)
        for prec in (16, 21, 27, 29, 30):
            self.check_method("exp", "-9E-8", prec=prec)

    def test_exp_tiny_inputs_at_minimum_normal(self):
        # CPython's C implementation rounds very tiny inputs to one in its work
        # context, whereas _pydecimal reports tininess for all negative inputs.
        for prec in (1, 3, 9, 28):
            context = decimal.Context(prec=prec, Emin=0, Emax=0)
            context.clear_traps()
            for coefficient, tiny in (("8.9", True), ("9", True), ("9.1", False)):
                context.clear_flags()
                value = decimal.Decimal(f"-{coefficient}E{-prec-4}")
                result = value.exp(context=context)
                self.assertEqual(result.as_tuple(), decimal.Decimal("1" + "0" * (prec - 1) + f"E{1-prec}").as_tuple())
                expected_flags = ["Inexact", "Rounded"] if tiny else ["Inexact", "Rounded", "Subnormal", "Underflow"]
                self.assertEqual(enabled_flags(context), expected_flags)

    def test_random_values(self):
        rng = random.Random(80152)
        for i in range(100):
            value = f"{rng.randrange(1, 10**40)}E{rng.randrange(-65, -15)}"
            for name in self.methods:
                self.check_method(name, value, prec=rng.choice((1, 3, 19, 28, 50)),
                                  Emin=-20, Emax=20, rounding=rng.choice(ROUNDINGS), clamp=i % 2)

    def test_explicit_context_and_traps(self):
        with decimal.localcontext(prec=1) as current:
            current.clear_flags()
            context = decimal.Context(prec=9, rounding=decimal.ROUND_DOWN)
            context.clear_traps()
            for name in self.methods:
                context.clear_flags()
                value = decimal.Decimal("2")
                self.assertEqual(getattr(value, name)(context), getattr(value, name)(context=context))
                self.assertEqual(enabled_flags(context), ["Inexact", "Rounded"])
                self.assertFalse(any(current.flags.values()))
                self.assertEqual(context.rounding, decimal.ROUND_DOWN)
                context.traps[decimal.Inexact] = True
                context.clear_flags()
                with self.assertRaises(decimal.Inexact):
                    getattr(value, name)(context=context)
                self.assertEqual(enabled_flags(context), ["Inexact", "Rounded"])
                context.traps[decimal.Inexact] = False
            context.traps[decimal.InvalidOperation] = True
            for name in self.methods:
                with self.assertRaises(decimal.InvalidOperation):
                    getattr(decimal.Decimal("sNaN"), name)(context=context)
            for name in ("ln", "log10"):
                with self.assertRaises(decimal.InvalidOperation):
                    getattr(decimal.Decimal("-1"), name)(context=context)
                context.clear_flags()
                self.assertEqual(getattr(decimal.Decimal("-0"), name)(context=context), decimal.Decimal("-Infinity"))
                self.assertFalse(any(context.flags.values()))
            context.traps[decimal.Overflow] = True
            with self.assertRaises(decimal.Overflow):
                decimal.Decimal("1E100").exp(context=context)
            context.traps[decimal.Underflow] = True
            with self.assertRaises(decimal.Underflow):
                decimal.Decimal("-1E100").exp(context=context)

    def test_argument_validation_and_subclasses(self):
        class Subclass(decimal.Decimal):
            pass

        value = Subclass("2")
        for name in self.methods:
            method = getattr(value, name)
            self.assertIs(type(method()), decimal.Decimal)
            self.assertEqual(method(), method(None))
            for context in (1, "context", object()):
                with self.assertRaises(TypeError):
                    method(context=context)
            with self.assertRaises(TypeError):
                method(extra=1)
            with self.assertRaises(TypeError):
                method(None, None)
            with self.assertRaises(TypeError):
                method(None, context=None)
            with self.assertRaises(TypeError):
                getattr(decimal.Decimal, name)(self=value)


class DecimalCompatibilityTests(unittest.TestCase):
    def test_extreme_tuple_exponents(self):
        cases = (
            (2**63 - 1, (), "-0E+999999", {public_decimal.Clamped}),
            (-2**63, (), "-0E-1000007", {public_decimal.Clamped}),
            (2**63 - 1, (0, 1, 2), "-Infinity",
             {public_decimal.Overflow, public_decimal.Inexact, public_decimal.Rounded}),
            (-2**63, (0, 1, 2), "-0E-1000007",
             {public_decimal.Underflow, public_decimal.Subnormal, public_decimal.Inexact,
              public_decimal.Rounded, public_decimal.Clamped}),
        )
        for exponent, digits, expected, flags in cases:
            for sequence in (tuple, list):
                with self.subTest(exponent=exponent, digits=digits, sequence=sequence):
                    context = public_decimal.Context(prec=9, traps=[])
                    value = sequence((1, digits, exponent))
                    self.assertEqual(str(context.create_decimal(value)), expected)
                    self.assertEqual({signal for signal, enabled in context.flags.items() if enabled}, flags)
                    with self.assertRaises(public_decimal.InvalidOperation):
                        public_decimal.Decimal(value)

    def test_extreme_tuple_traps_and_integer_overflow(self):
        context = public_decimal.Context(prec=9)
        context.traps[public_decimal.Underflow] = True
        for exponent, signal in ((2**63 - 1, public_decimal.Overflow), (-2**63, public_decimal.Underflow)):
            with self.assertRaises(signal):
                context.create_decimal((1, (0, 1, 2), exponent))
        for exponent in (2**63, -2**63 - 1):
            for constructor in (public_decimal.Decimal, context.create_decimal):
                with self.assertRaises(OverflowError):
                    constructor((1, (), exponent))

    def test_extreme_string_exponents(self):
        class String(str):
            pass

        for exponent in (2**63 - 1, 2**63, 10**100, -2**63, -2**63 - 1, -10**100):
            for coefficient in ("-0", "-0.0", "-12", "-1.2"):
                for string_type in (str, String):
                    with self.subTest(exponent=exponent, coefficient=coefficient, string_type=string_type):
                        context = public_decimal.Context(prec=9, traps=[])
                        value = string_type(f"{coefficient}e{exponent}")
                        if coefficient in ("-0", "-0.0"):
                            expected = "-0E+999999" if exponent > 0 else "-0E-1000007"
                            flags = {public_decimal.Clamped}
                        elif exponent > 0:
                            expected = "-Infinity"
                            flags = {public_decimal.Overflow, public_decimal.Inexact, public_decimal.Rounded}
                        else:
                            expected = "-0E-1000007"
                            flags = {public_decimal.Underflow, public_decimal.Subnormal, public_decimal.Inexact,
                                     public_decimal.Rounded, public_decimal.Clamped}
                        self.assertEqual(str(context.create_decimal(value)), expected)
                        self.assertEqual({signal for signal, enabled in context.flags.items() if enabled}, flags)
                        with self.assertRaises(public_decimal.InvalidOperation):
                            public_decimal.Decimal(value)

    def test_extreme_string_traps_and_syntax(self):
        context = public_decimal.Context(prec=9)
        context.traps[public_decimal.Underflow] = True
        for value, signal in (("1e9223372036854775808", public_decimal.Overflow),
                              ("1.2e-9223372036854775808", public_decimal.Underflow)):
            with self.assertRaises(signal):
                context.create_decimal(value)
        for value in ("1e999999999999999999999x", "1e+", " 1e999999999999999999999", "1_0"):
            with self.assertRaises(public_decimal.InvalidOperation):
                context.create_decimal(value)

    def test_context_conversion_for_all_input_types(self):
        class Integer(int):
            pass

        class Float(float):
            pass

        class String(str):
            pass

        class Decimal(public_decimal.Decimal):
            pass

        cases = [12345, 2**40, 10**100, Integer(12345), True, False,
                 1.1, Float(1.1), 5e-324, float("inf"), float("nan"),
                 "1.2345", "1e99", "1e-99", String("1.2345"),
                 public_decimal.Decimal("1.2345"), Decimal("1.2345")]
        for value in cases:
            with self.subTest(value=value, type=type(value)):
                reference_value = (reference.Decimal(str(value))
                                   if isinstance(value, public_decimal.Decimal) else value)
                context = public_decimal.Context(prec=3, Emin=-3, Emax=3, traps=[])
                expected_context = reference.Context(prec=3, Emin=-3, Emax=3, traps=[])
                result = context.create_decimal(value)
                expected = expected_context.create_decimal(reference_value)
                self.assertEqual(str(result), str(expected))
                self.assertEqual(enabled_flags(context), enabled_flags(expected_context))

    def test_localcontext_argument_binding(self):
        context = public_decimal.Context(prec=9)
        with public_decimal.localcontext(ctx=context, prec=3, rounding=public_decimal.ROUND_DOWN) as local:
            self.assertEqual(local.prec, 3)
            self.assertEqual(local.rounding, public_decimal.ROUND_DOWN)
        self.assertEqual(context.prec, 9)
        with public_decimal.localcontext(context, 3, public_decimal.ROUND_DOWN) as local:
            self.assertEqual(local.prec, 3)
            self.assertEqual(local.rounding, public_decimal.ROUND_DOWN)
        with self.assertRaises(TypeError):
            public_decimal.localcontext(context=context)
        with self.assertRaises(TypeError):
            public_decimal.setcontext(context=context)
        with self.assertRaises(TypeError):
            context.create_decimal(num="1")
        with self.assertRaises(TypeError):
            context.create_decimal_from_float(f=1.0)
        self.assertEqual(context.create_decimal(), public_decimal.Decimal(0))

    def test_cpython_argument_names(self):
        for function, names in (
            (public_decimal.setcontext, ["context"]),
            (public_decimal.Context.create_decimal, ["self", "num"]),
            (public_decimal.Context.create_decimal_from_float, ["self", "f"]),
        ):
            self.assertEqual(list(inspect.signature(function).parameters), names)


class DecimalCompactTests(unittest.TestCase):
    def check_add_subtract(self, left, right, **settings):
        for operation in (operator.add, operator.sub):
            results = []
            for module in (decimal, reference):
                with module.localcontext(**settings) as context:
                    context.clear_traps()
                    context.clear_flags()
                    result = operation(module.Decimal(left), module.Decimal(right))
                    results.append((repr(result), enabled_flags(context)))
            self.assertEqual(results[0], results[1], (left, right, operation.__name__, settings))

    def test_different_exponents(self):
        pairs = (("1.2", "0.03"), ("1.20", "1.2"), ("1E+18", "1"),
                 ("9E+18", "1"), ("1E+19", "1"),
                 ("922337203685477580E+1", "7"),  # largest compact sum
                 ("922337203685477580E+1", "8"),  # sum overflows
                 ("922337203685477581E+1", "1"),  # scaling overflows
                 ("0E+999999999999999999", "1.00"),
                 ("0E+999999999999999999", "0E-1999999999999999997"),
                 ("0E-1999999999999999997", "1"),
                 ("1E+999999999999999999", "1E-1999999999999999997"),
                 ("1E-4", "0.00010"), ("999E+2", "1"))
        for rounding in ROUNDINGS:
            for prec, emin, emax, clamp in ((28, -999999, 999999, 0), (19, -20, 20, 0),
                                          (3, -3, 3, 0), (3, -3, 3, 1)):
                for a, b in pairs:
                    for left, right in ((a, b), (b, a)):
                        for left_sign, right_sign in (("", ""), ("-", ""), ("", "-"), ("-", "-")):
                            self.check_add_subtract(left_sign + left, right_sign + right, prec=prec,
                                                    rounding=rounding, Emin=emin, Emax=emax, clamp=clamp)

    def test_random_compact_operands(self):
        rng = random.Random(80152)
        for i in range(300):
            left = f"{rng.randrange(-(2**63 - 1), 2**63)}e{rng.randrange(-20, 21)}"
            right = f"{rng.randrange(-(2**63 - 1), 2**63)}e{rng.randrange(-20, 21)}"
            self.check_add_subtract(left, right, prec=rng.choice((3, 19, 28, 50)),
                                    rounding=rng.choice(ROUNDINGS), Emin=-20, Emax=20, clamp=i % 2)

    def test_integer_conversion(self):
        for coefficient in (0, 1, 9, 10, 12345, 922337203685477580, 922337203685477581, 2**63 - 1):
            for exponent in (-1999999999999999997, -20, -19, -18, -5, -1, 0, 1, 5, 18, 19, 20):
                for sign in ("", "-"):
                    text = f"{sign}{coefficient}e{exponent}"
                    with decimal.localcontext(prec=1, Emin=-1, Emax=1) as context:
                        context.clear_flags()
                        self.assertEqual(int(decimal.Decimal(text)), int(reference.Decimal(text)), text)
                        self.assertFalse(any(context.flags.values()), text)
        for sign in ("", "-"):
            self.assertEqual(int(decimal.Decimal(f"{sign}0E+999999999999999999")), 0)


class DecimalCompactOptimizationTests(unittest.TestCase):
    def test_compact_formatting(self):
        coefficients = {0, 1, 9, 10, 100, 2**63 - 1}
        for power in range(1, 19):
            coefficients.update((10**power - 1, 10**power, 10**power + 1))
        exponents = tuple(range(-26, 27)) + (-1999999999999999997, 999999999999999999)
        for capitals in (0, 1):
            with (decimal.localcontext(capitals=capitals) as actual_context,
                  reference.localcontext(capitals=capitals) as expected_context):
                actual_context.clear_flags()
                for coefficient in sorted(coefficients):
                    for sign in (0, 1):
                        for exponent in exponents:
                            valid_exponent = (min(exponent, decimal.MAX_EMAX - len(str(coefficient)) + 1)
                                              if coefficient else exponent)
                            value = (sign, tuple(map(int, str(coefficient))), valid_exponent)
                            actual = decimal.Decimal(value)
                            expected = reference.Decimal(value)
                            self.assertEqual(str(actual), str(expected), value)
                            self.assertEqual(actual_context.to_sci_string(actual),
                                             expected_context.to_sci_string(expected), value)
                self.assertFalse(any(actual_context.flags.values()))

    def test_formatting_fallbacks(self):
        for capitals in (0, 1):
            with (decimal.localcontext(capitals=capitals) as actual_context,
                  reference.localcontext(capitals=capitals) as expected_context):
                for text in ('Infinity', '-Infinity', 'NaN', '-NaN123', 'sNaN9', '9223372036854775808',
                             '123456789012345678901234567890.00', '0E-7', '-0.000000', '1.2300E+30'):
                    actual = decimal.Decimal(text)
                    expected = reference.Decimal(text)
                    self.assertEqual(str(actual), str(expected), text)
                    self.assertEqual(actual_context.to_sci_string(actual),
                                     expected_context.to_sci_string(expected), text)

    def test_digit_count_boundaries(self):
        coefficients = {0, 1, 2**63 - 1}
        for power in range(1, 19):
            coefficients.update((10**power - 1, 10**power, 10**power + 1))
        rng = random.Random(80152)
        coefficients.update(rng.randrange(1, 2**63) for _ in range(200))
        for coefficient in coefficients:
            for precision in (max(1, len(str(coefficient)) - 1), len(str(coefficient)), 28):
                with decimal.localcontext(prec=precision), reference.localcontext(prec=precision):
                    self.assertEqual(str(+decimal.Decimal(coefficient)), str(+reference.Decimal(coefficient)),
                                     (coefficient, precision))

    def test_scale_overflow_boundaries(self):
        for shift in range(1, 21):
            limit = (2**63 - 1) // 10**shift
            for coefficient in (0, 1, max(0, limit - 1), limit, limit + 1):
                for sign in ('', '-'):
                    text = f'{sign}{coefficient}E+{shift}'
                    with decimal.localcontext(prec=40), reference.localcontext(prec=40):
                        actual = decimal.Decimal(text) + decimal.Decimal('1')
                        expected = reference.Decimal(text) + reference.Decimal('1')
                        self.assertEqual(str(actual), str(expected), text)


class ContextLookupOptimizationTests(unittest.TestCase):
    def test_contextvar_set_reset_and_defaults(self):
        value = contextvars.ContextVar('value')
        other = contextvars.ContextVar('other')
        self.assertEqual(value.get(1), 1)
        self.assertEqual(value.get(2), 2)
        outer = value.set('outer')
        for _ in range(2000):
            self.assertEqual(value.get(), 'outer')
        changed = other.set('unrelated')
        self.assertEqual(value.get(), 'outer')
        inner = value.set(None)
        self.assertIsNone(value.get())
        value.reset(inner)
        self.assertEqual(value.get(), 'outer')
        other.reset(changed)
        self.assertEqual(value.get(), 'outer')
        value.reset(outer)
        self.assertEqual(value.get('missing'), 'missing')
        with self.assertRaises(LookupError):
            value.get()

    def test_missing_contextvar_does_not_retain_default(self):
        class Default:
            pass

        def read_default():
            default = Default()
            var = contextvars.ContextVar('missing', default=default)
            self.assertIs(var.get(), default)
            return weakref.ref(default)

        context = contextvars.Context()
        reference = context.run(read_default)
        gc.collect()
        self.assertIsNone(reference())
        self.assertEqual(len(context), 0)

    def test_alternating_lookup_sites(self):
        a = contextvars.ContextVar('a')
        b = contextvars.ContextVar('b')
        context = contextvars.Context()
        context.run(a.set, 1)
        context.run(b.set, 2)

        def read():
            return a.get(), b.get()

        for _ in range(20000):
            self.assertEqual(context.run(read), (1, 2))
        copied = context.copy()
        copied.run(a.set, 3)
        self.assertEqual(copied.run(read), (3, 2))
        self.assertEqual(context.run(read), (1, 2))
        context.run(b.set, None)
        self.assertEqual(context.run(read), (1, None))

    def test_lookup_cache_limit_and_variable_guard(self):
        a = contextvars.ContextVar('a')
        b = contextvars.ContextVar('b')
        contexts = [contextvars.Context() for _ in range(5)]
        for i, context in enumerate(contexts):
            context.run(a.set, i)
            context.run(b.set, i + 10)

        def read(var, default=None):
            return var.get(default)

        # Fill three entries, then exceed the limit while keeping all maps alive.
        for context, var, expected in ((contexts[0], a, 0), (contexts[0], b, 10), (contexts[1], a, 1)):
            for _ in range(2000):
                self.assertEqual(context.run(read, var), expected)
        for i, context in enumerate(contexts):
            self.assertEqual(context.run(read, a), i)
            self.assertEqual(context.run(read, b), i + 10)
        contexts[0].run(a.set, None)
        self.assertIsNone(contexts[0].run(read, a))

        def read_missing(default):
            return a.get(default)

        empty = contextvars.Context()
        for default in (1, 2, None):
            self.assertEqual(empty.run(read_missing, default), default)
        empty.run(a.set, 42)
        self.assertEqual(empty.run(read_missing, 3), 42)

    def test_reset_does_not_retain_cached_value(self):
        class Value:
            pass

        var = contextvars.ContextVar('value')

        def read_and_reset():
            value = Value()
            token = var.set(value)
            self.assertIs(var.get(), value)
            reference = weakref.ref(value)
            var.reset(token)
            return reference

        context = contextvars.Context()
        reference = context.run(read_and_reset)
        gc.collect()
        self.assertIsNone(reference())

    def test_copied_contexts_and_cache_interference(self):
        a = contextvars.ContextVar('a')
        b = contextvars.ContextVar('b')
        original = contextvars.Context()
        original.run(a.set, 'original')
        for _ in range(2000):
            self.assertEqual(original.run(a.get), 'original')
        copied = original.copy()
        copied.run(a.set, 'copy')
        copied.run(b.set, 'other')
        self.assertEqual(copied.run(b.get), 'other')
        self.assertEqual(copied.run(a.get), 'copy')
        self.assertEqual(original.run(a.get), 'original')
        fresh = contextvars.Context()
        self.assertEqual(fresh.run(a.get, 'missing'), 'missing')
        token = fresh.run(a.set, 'fresh')
        self.assertEqual(fresh.run(a.get), 'fresh')
        fresh.run(a.reset, token)
        self.assertEqual(fresh.run(a.get, 'missing'), 'missing')

    def test_decimal_context_transitions(self):
        def calculate():
            return decimal.Decimal('1.2345') + decimal.Decimal('0')

        original = decimal.getcontext()
        try:
            decimal.setcontext(decimal.Context(prec=3, rounding=decimal.ROUND_DOWN))
            for _ in range(20000):
                self.assertEqual(str(calculate()), '1.23')
            unrelated = contextvars.ContextVar('unrelated')
            token = unrelated.set(42)
            self.assertEqual(str(calculate()), '1.23')
            unrelated.reset(token)
            self.assertEqual(str(calculate()), '1.23')
            with decimal.localcontext(prec=2):
                self.assertEqual(str(calculate()), '1.2')
                copied = contextvars.copy_context()
            self.assertEqual(str(calculate()), '1.23')
            self.assertEqual(str(copied.run(calculate)), '1.2')
            decimal.getcontext().prec = 4
            self.assertEqual(str(calculate()), '1.234')
            decimal.setcontext(decimal.Context(prec=2, rounding=decimal.ROUND_UP))
            self.assertEqual(str(calculate()), '1.3')
            fresh = contextvars.Context()
            self.assertEqual(str(fresh.run(calculate)), '1.2345')
            self.assertEqual(str(calculate()), '1.3')
            results = []
            thread = threading.Thread(target=lambda: results.append(str(calculate())))
            thread.start()
            thread.join()
            self.assertEqual(results, ['1.2345'])
            self.assertEqual(str(calculate()), '1.3')
        finally:
            decimal.setcontext(original)

    def test_rounding_changes_after_warmup(self):
        actual = decimal.Context(rounding=decimal.ROUND_DOWN)
        expected = reference.Context(rounding=reference.ROUND_DOWN)
        quantum = decimal.Decimal('0.01')

        def quantize(value, rounding=None):
            return value.quantize(quantum, rounding=rounding, context=actual)

        value = decimal.Decimal('1.235')
        for _ in range(20000):
            self.assertEqual(str(quantize(value)), '1.23')
        for rounding in ROUNDINGS:
            actual.rounding = getattr(decimal, rounding)
            expected.rounding = getattr(reference, rounding)
            for text in ('1.235', '-1.235', '1.234', '-1.234', '0.005', '-0.005', '1.250', '0'):
                self.assertEqual(str(quantize(decimal.Decimal(text))),
                                 str(expected.quantize(reference.Decimal(text), reference.Decimal('0.01'))))
            self.assertEqual(str(quantize(value, decimal.ROUND_DOWN)), '1.23')
        actual.prec = expected.prec = 2
        actual.traps[decimal.InvalidOperation] = False
        expected.traps[reference.InvalidOperation] = False
        self.assertEqual(str(quantize(value)),
                         str(expected.quantize(reference.Decimal('1.235'), reference.Decimal('0.01'))))
        self.assertTrue(actual.flags[decimal.InvalidOperation])


def load_tests(loader, tests, pattern):
    # unittest normally sorts classes by name; keep the basic suites first.
    return unittest.TestSuite(loader.loadTestsFromTestCase(test_case) for test_case in (
        DecimalInitializationTests,
        DecimalConstructionTests,
        DecimalStringCreationTests,
        DecimalContextTests,
        DecimalSignalDictTests,
        DecimalNumericSlotsTests,
        DecimalDunderTests,
        DecimalMethodTests,
        DecimalContextMethodTests,
        DecimalQuantizeTests,
        DecimalTranscendentalTests,
        DecimalCompatibilityTests,
        DecimalCompactTests,
        DecimalCompactOptimizationTests,
        ContextLookupOptimizationTests,
    ))


if __name__ == '__main__':
    unittest.main()
