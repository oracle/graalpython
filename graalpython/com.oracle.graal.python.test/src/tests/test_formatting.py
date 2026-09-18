# Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import unittest


def test_named_format_fields():
    from string import Formatter
    from types import SimpleNamespace

    names = ('id', 'style', '_private', 'name_1', 'caf\u00e9', '\u540d\u524d',
             '\U0001f600', '\u00b2', ' ', '+', '-', '-1', '-0', '-00', '+0', '+1',
             '-\u0660', '+\uff11', '1_name', '1\u00b2', '\U0001d7d9_name')
    for name in names:
        template = '{' + name + '}'
        assert template.format(**{name: 'value'}) == 'value'
        assert template.format_map({name: 'value'}) == 'value'
        assert ('{data[' + name + ']}').format(data={name: 'item'}) == 'item'
        assert Formatter().format(template, **{name: 'value'}) == 'value'
        assert Formatter().format('{data[' + name + ']}', data={name: 'item'}) == 'item'

    style = SimpleNamespace(font_family='sans-serif', sizes={'title': 20})
    assert '{id}: {style.font_family} {style.sizes[title]}'.format(id='chart', style=style) == 'chart: sans-serif 20'
    assert '{value:{width}.{precision}f}'.format(value=1.25, width=6, precision=2) == '  1.25'
    with unittest.TestCase().assertRaises(KeyError):
        '{missing}'.format(present=1)


def test_format_field_numeric_indices():
    from string import Formatter

    for index in ('1', '01', '\u0661', '\uff11', '\U0001d7d9', '\u0660\U0001d7d9', '0' * 100 + '1'):
        assert ('{' + index + '}').format('zero', 'one') == 'one'
        assert ('{data[' + index + ']}').format(data={1: 'one'}) == 'one'
        assert Formatter().format('{' + index + '}', 'zero', 'one') == 'one'
        assert Formatter().format('{data[' + index + ']}', data={1: 'one'}) == 'one'
    assert '{} {}'.format('zero', 'one') == 'zero one'
    assert '{0} {name}'.format('zero', name='one') == 'zero one'
    with unittest.TestCase().assertRaises(IndexError):
        '{2}'.format('zero', 'one')
    with unittest.TestCase().assertRaises(ValueError):
        ('{' + '9' * 100 + '}').format('zero')
    with unittest.TestCase().assertRaises(ValueError):
        '{0} {}'.format('zero', 'one')


def test_format_field_integer_overflow():
    import sys

    maximum = str(sys.maxsize)
    assert ('{data[' + maximum + ']}').format(data={sys.maxsize: 'maximum'}) == 'maximum'
    assert ('{data[' + '0' * 100 + maximum + ']}').format(data={sys.maxsize: 'maximum'}) == 'maximum'
    with unittest.TestCase().assertRaises(IndexError):
        ('{' + maximum + '}').format('zero')

    # A non-digit before overflow makes the field a string key.
    name = maximum + '_name'
    assert ('{' + name + '}').format(**{name: 'named'}) == 'named'
    assert ('{data[' + name + ']}').format(data={name: 'named'}) == 'named'

    # Overflow is detected immediately, even when a non-digit follows it.
    overflow = str(sys.maxsize + 1)
    for name in (overflow, overflow + '_name', maximum + '0', '9' * 100,
                 ''.join(chr(0x0660 + int(digit)) for digit in overflow)):
        with unittest.TestCase().assertRaisesRegex(ValueError, 'Too many decimal digits in format string'):
            ('{' + name + '}').format(**{name: 'named'})
        with unittest.TestCase().assertRaisesRegex(ValueError, 'Too many decimal digits in format string'):
            ('{data[' + name + ']}').format(data={name: 'named'})


def test_formatter_field_name_integer_parsing():
    import _string
    import sys

    for name in ('', '-0', '-00', '+0', '+1', str(sys.maxsize) + '_name'):
        first, rest = _string.formatter_field_name_split(name)
        assert first == name
        assert list(rest) == []
    first, rest = _string.formatter_field_name_split(str(sys.maxsize))
    assert first == sys.maxsize
    assert list(rest) == []
    for name in (str(sys.maxsize + 1), str(sys.maxsize + 1) + '_name'):
        with unittest.TestCase().assertRaisesRegex(ValueError, 'Too many decimal digits in format string'):
            _string.formatter_field_name_split(name)


class Polymorph:
    def __index__(self):
        return 42
    def __int__(self):
        return 1
    def __float__(self):
        return 3.14
    def __str__(self):
        return "hello"
    def __bytes__(self):
        return b"bytes"


# This is all one needs to implement to satisfy PyCheck_Mapping
class MyPseudoMapping:
    def __getitem__(self, item):
        return item


def test_formatting():
    # tests some corner-cases that the standard tests do not cover
    assert format(-12e8, "0=30,.4f") == '-0,000,000,001,200,000,000.0000'
    assert b"%(mykey)d" % {b'mykey': 42} == b"42"
    assert b"%c" % b'q' == b"q"
    assert "%-5d" % 42 == "42   "
    assert "%.*f" % (-2, 2.5) == "2"
    assert "%.*f" % (True, 2.51) == "2.5"
    assert "%ld" % 42 == "42"

    assert "%c" % Polymorph() == "*"
    assert "%d" % Polymorph() == "1"
    assert "%x" % Polymorph() == "2a"
    assert "%s" % Polymorph() == "hello"
    assert "%.2f" % Polymorph() == "3.14"
    assert b"%c" % Polymorph() == b"*"
    assert b"%s" % Polymorph() == b"bytes"

    assert type(bytearray("hello %d", "ascii") % 42) == bytearray
    assert type(b"hello %d" % 42) == bytes

    # No error about too many arguments,
    # because the object is considered as a mapping...
    assert " " % MyPseudoMapping() == " "

    # Localized format still honors the sign specifier
    assert format(1234.5, "+n").startswith("+")


def test_int_format():
    class PolymorphInt(int):
        def __float__(self):
            return 42.5

    assert format(PolymorphInt(2), "g") == '42.5'


class MyComplex(complex):
    def __repr__(self):
        return 'wrong answer'
    def __str__(self):
        return '42'


def test_complex_formatting():
    assert format(3+2j, ">20,.4f") == "      3.0000+2.0000j"
    assert format(3+2j, "+.2f") == "+3.00+2.00j"
    assert format(-3+2j, "+.2f") == "-3.00+2.00j"
    assert format(3+2j, "-.3f") == "3.000+2.000j"
    assert format(3-2j, "-.3f") == "3.000-2.000j"
    assert format(-3-2j, "-.3f") == "-3.000-2.000j"
    assert format(3+2j, " .1f") == " 3.0+2.0j"
    assert format(-3+2j, " .1f") == "-3.0+2.0j"
    assert format(complex(3), ".1g") == "3+0j"
    assert format(3j, ".1g") == "0+3j"
    assert format(-3j, ".1g") == "-0-3j"
    assert format(3j, "") == "3j"
    assert format(1+0j, "") == "(1+0j)"
    assert format(1+2j, "") == "(1+2j)"
    assert format(complex(1, float("NaN")), "") == "(1+nanj)"
    assert format(complex(1, float("Inf")), "") == "(1+infj)"
    assert format(MyComplex(3j), "") == "42"
    assert format(MyComplex(3j), " <5") == "3j   "
    assert format(complex(2**53 + 1, 0)) == '(9007199254740992+0j)'
    assert format(1j, "+.1") == "+1j"
    assert format(1j, " .2") == " 1j"
    assert format(1000j, ",.4") == "1,000j"
    assert format(1000+2000j, ",") == "(1,000+2,000j)"
    assert format(1000j, "#") == "1000.j"
    assert format(1+2j, "#") == "(1.+2.j)"
    assert format(0j, "+.0") == "+0j"
    assert format(1+2j, "+") == "(+1+2j)"


def test_alternate_float_formatting():
    assert format(0.0, ".0") == "0e+00"
    assert format(1000.0, "#") == "1000.0"
    assert format(6.103515625e-05, "#.11g") == "6.1035156250e-05"
    assert format(2.220446049250313e-16, "#.038g") == "2.2204460492503130808472633361816406250e-16"
    assert format(2.220446049250313e-16j, "#.038g") == (
        "0.0000000000000000000000000000000000000+2.2204460492503130808472633361816406250e-16j")


class AnyRepr:
    def __init__(self, val):
        self.val = val
    def __repr__(self):
        return self.val


def test_non_ascii_repr():
    assert "%a" % AnyRepr("\t") == "\t"
    assert "%a" % AnyRepr("\\") == "\\"
    assert "%a" % AnyRepr("\\") == "\\"
    assert "%a" % AnyRepr("\u0378") == "\\u0378"
    assert "%r" % AnyRepr("\u0378") == "\u0378"
    assert "%a" % AnyRepr("\u0374") == "\\u0374"
    assert "%r" % AnyRepr("\u0374") == "\u0374"

    assert b"%a" % AnyRepr("\t") == b"\t"
    assert b"%a" % AnyRepr("\\") == b"\\"
    assert b"%a" % AnyRepr("\\") == b"\\"
    assert b"%a" % AnyRepr("\u0378") == b"\\u0378"
    assert b"%r" % AnyRepr("\u0378") == b"\\u0378"
    assert b"%a" % AnyRepr("\u0374") == b"\\u0374"
    assert b"%r" % AnyRepr("\u0374") == b"\\u0374"


class FormattingErrorsTest(unittest.TestCase):
    def test_formatting_errors(self):
        self.assertRaises(TypeError, lambda: format(-12e8, b"0=30,.4f"))
        self.assertRaises(TypeError, lambda: format(42, b"0=30,.4f"))
        self.assertRaises(TypeError, lambda: format("str", b"0=30,.4f"))
        self.assertRaises(TypeError, lambda: format(3+1j, b"0=30,.4f"))
        self.assertRaises(TypeError, lambda: b"hello" % b"world")
        self.assertRaises(TypeError, lambda: b"%f" % "str")
        self.assertRaises(TypeError, lambda: b"%c" % "str")

        self.assertRaises(KeyError, lambda: b"%(mykey)d" % {"mykey": 42})
        self.assertRaises(KeyError, lambda: "%(mykey)d" % {b"mykey": 42})
        self.assertRaises(OverflowError, lambda: b"%c" % 260)

        self.assertRaises(ValueError, lambda: format(3+2j, "f=30,.4f"))
        self.assertRaises(ValueError, lambda: format(3+2j, "0=30,.4f"))


def test_overridden_str():
    class MyInt(int):
        def __str__(self):
            return '42'

    class MyFloat(float):
        def __str__(self):
            return "__str__ overridden for float"

    # floats w/o type specifier, but with other flags should produce
    # something like __str__ but not actually call __str__. Only when
    # the formatting string is empty it calls actual __str__.
    assert "{}".format(MyFloat(2)) == "__str__ overridden for float"
    assert "{0:10}".format(MyFloat(2)) == "       2.0"
    assert format(MyFloat(2), "") == "__str__ overridden for float"
    assert format(MyFloat(2), "5") == "  2.0"

    assert format(10000.0, '_') == "10_000.0"
    assert format(10000.0, '') == "10000.0"
    # if precision is set use '%g' instead of the __str__ like formatting:
    assert format(10000.0, "+,.3") == "+1e+04"

    assert "{}".format(MyInt(2)) == "42"
    assert "{0:10}".format(MyInt(2)) == "         2"
    assert format(MyInt(2), "")  == "42"
    assert format(MyInt(2), "5") == "    2"


def test_fstring():
    class CustomFormat:
        def __format__(self, format_spec):
            return format_spec

    x = CustomFormat()
    assert f'{x:={1=}}' == "=1=1"
    # blank spaces after '=' are fine
    assert f'{42=   :<10}' == '42=   42        '
    # curly braces in expressions
    assert f'{len({})}' == '0'
    assert f'{10:#{(len({1,2,3,4,5}))}}' == '   10'
    # square brackets in expressions
    aligns = ['<', '>']
    align = 0
    assert f'{3:{aligns[align]}{5}}' == '3    '
    # this is not walrus but 'x' with a format specifier "=10"
    x = 20
    assert f'{x:=10}' == '        20'
