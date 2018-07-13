# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import re
import string
import unittest


def test_match():
    md = re.compile('hello').match('hello world')
    assert md.groups() == ()
    assert md.group(0) == "hello"

    assert re.compile("hi").match("hello") is None
    assert re.compile("ello").match("hello") is None
    assert re.compile("^hello").match("hello")
    assert re.compile("^hello").search("hello")
    assert re.compile("ello").search("hello")


def test_grouping():
    md = re.compile('he(l)l(o)').match('hello world')
    assert md.groups() == ("l", "o")
    assert md.group(0) == "hello"
    assert md.group(1) == "l"
    assert md.group(2) == "o"
    assert md.start(0) == 0
    assert md.start(1) == 2
    assert md.end(1) == 3
    assert md.end(0) == 5


def test_grouping2():
    md = re.compile('he(l)l(?:o)').match('hello world')
    assert md.groups() == ("l",)
    assert md.group(0) == "hello"
    assert md.group(1) == "l"
    assert md.start(0) == 0
    assert md.start(1) == 2
    assert md.end(1) == 3
    assert md.end(0) == 5


def test_ignorecase():
    md = re.compile('he(l)l(?:o)', re.IGNORECASE).match('HELLO world')
    assert md.group(1) == "L"
    assert md.group(0) == "HELLO"


def test_multiline_search():
    md = re.compile('he(l)l(?:o)', re.MULTILINE).search('asas\nashello world')
    assert md.group(0) == "hello"


def test_multiline_findall():
    pattern = re.compile('he(l)l(?:o)', re.MULTILINE)
    assert pattern.findall('asas\nashello world\nhello\nas\nworhello') == ["l", "l", "l"]


def test_special_re_compile():
    _wordchars_re = re.compile(r'[^\\\'\"%s ]*' % string.whitespace)
    _squote_re = re.compile(r"'(?:[^'\\]|\\.)*'")
    _dquote_re = re.compile(r'"(?:[^"\\]|\\.)*"')


class S(str):
    def __getitem__(self, index):
        return S(super().__getitem__(index))


class B(bytes):
    def __getitem__(self, index):
        return B(super().__getitem__(index))


class ReTests(unittest.TestCase):
    def assertTypedEqual(self, actual, expect, msg=None):
        self.assertEqual(actual, expect, msg)
        def recurse(actual, expect):
            if isinstance(expect, (tuple, list)):
                for x, y in zip(actual, expect):
                    recurse(x, y)
            else:
                self.assertIs(type(actual), type(expect), msg)
        recurse(actual, expect)

    def test_search_star_plus(self):
        self.assertEqual(re.search('x*', 'axx').span(0), (0, 0))
        self.assertEqual(re.search('x*', 'axx').span(), (0, 0))
        self.assertEqual(re.search('x+', 'axx').span(0), (1, 3))
        self.assertEqual(re.search('x+', 'axx').span(), (1, 3))
        self.assertIsNone(re.search('x', 'aaa'))
        self.assertEqual(re.match('a*', 'xxx').span(0), (0, 0))
        self.assertEqual(re.match('a*', 'xxx').span(), (0, 0))
        self.assertEqual(re.match('x*', 'xxxa').span(0), (0, 3))
        self.assertEqual(re.match('x*', 'xxxa').span(), (0, 3))
        self.assertIsNone(re.match('a+', 'xxx'))

    def bump_num(self, matchobj):
        int_value = int(matchobj.group(0))
        return str(int_value + 1)

    def test_basic_re_sub(self):
        self.assertTypedEqual(re.sub('y', 'a', 'xyz'), 'xaz')
        # self.assertTypedEqual(re.sub('y', S('a'), S('xyz')), 'xaz')
        # self.assertTypedEqual(re.sub(b'y', b'a', b'xyz'), b'xaz')
        # self.assertTypedEqual(re.sub(b'y', B(b'a'), B(b'xyz')), b'xaz')
        self.assertTypedEqual(re.sub(b'y', bytearray(b'a'), bytearray(b'xyz')), b'xaz')
        # self.assertTypedEqual(re.sub(b'y', memoryview(b'a'), memoryview(b'xyz')), b'xaz')
        # for y in ("\xe0", "\u0430", "\U0001d49c"):
        #     self.assertEqual(re.sub(y, 'a', 'x%sz' % y), 'xaz')

        self.assertEqual(re.sub("(?i)b+", "x", "bbbb BBBB"), 'x x')
        self.assertEqual(re.sub(r'\d+', self.bump_num, '08.2 -2 23x99y'),
                         '9.3 -3 24x100y')
        self.assertEqual(re.sub(r'\d+', self.bump_num, '08.2 -2 23x99y', 3),
                         '9.3 -3 23x99y')
        self.assertEqual(re.sub(r'\d+', self.bump_num, '08.2 -2 23x99y', count=3),
                         '9.3 -3 23x99y')

        self.assertEqual(re.sub('.', lambda m: r"\n", 'x'), '\\n')
        # self.assertEqual(re.sub('.', r"\n", 'x'), '\n')

        s = r"\1\1"
        self.assertEqual(re.sub('(.)', s, 'x'), 'xx')
        self.assertEqual(re.sub('(.)', s.replace('\\', r'\\'), 'x'), s)
        self.assertEqual(re.sub('(.)', lambda m: s, 'x'), s)

        self.assertEqual(re.sub('(?P<a>x)', r'\g<a>\g<a>', 'xx'), 'xxxx')
        self.assertEqual(re.sub('(?P<a>x)', r'\g<a>\g<1>', 'xx'), 'xxxx')
        # self.assertEqual(re.sub('(?P<unk>x)', r'\g<unk>\g<unk>', 'xx'), 'xxxx')
        # self.assertEqual(re.sub('(?P<unk>x)', r'\g<1>\g<1>', 'xx'), 'xxxx')

        # self.assertEqual(re.sub('a', r'\t\n\v\r\f\a\b', 'a'), '\t\n\v\r\f\a\b')
        self.assertEqual(re.sub('a', '\t\n\v\r\f\a\b', 'a'), '\t\n\v\r\f\a\b')
        self.assertEqual(re.sub('a', '\t\n\v\r\f\a\b', 'a'),
                         (chr(9) + chr(10) + chr(11) + chr(13) + chr(12) + chr(7) + chr(8)))

        # self.assertEqual(re.sub(r'^\s*', 'X', 'test'), 'Xtest')

    def test_backreference(self):
        compiled = re.compile(r"(.)\1")
        self.assertTrue(compiled.match("11"))
        self.assertTrue(compiled.match("22"))
        self.assertFalse(compiled.match("23"))

        compiled = re.compile(r"\b(\w*)\b\W\1")
        self.assertTrue(compiled.match("hello hello"))
        self.assertTrue(compiled.match("world*world"))
        self.assertFalse(compiled.match("oh no"))

        compiled = re.compile(r"(\d).\d.\d-\1")
        self.assertEqual((0, 7), compiled.match("1.2.3-1").span())

    def test_escaping(self):
        regex = None
        try:
            regex = re.compile(r"""        # A numeric string consists of:
    #    \s*
        (?P<sign>[-+])?              # an optional sign, followed by either...
        (
            (?=\d|\.\d)              # ...a number (with at least one digit)
            (?P<int>\d*)             # having a (possibly empty) integer part
            (\.(?P<frac>\d*))?       # followed by an optional fractional part
            (E(?P<exp>[-+]?\d+))?    # followed by an optional exponent, or...
        |
            Inf(inity)?              # ...an infinity, or...
        |
            (?P<signal>s)?           # ...an (optionally signaling)
            NaN                      # NaN
            (?P<diag>\d*)            # with (possibly empty) diagnostic info.
        )
    #    \s*
        \Z
            """, re.VERBOSE)
        except:
            self.fail()

        match = regex.search("  -12.1")
        self.assertTrue(match)
        # assert "frac" in match.groupdict()
        # assert match.groupdict()["frac"] == "1"

