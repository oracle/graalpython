# Copyright (c) 2019, 2025, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import re
import string
import unittest
import sys


class IntegerLike:
    def __init__(self, value):
        self.value = value

    def __index__(self):
        return self.value



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


def test_pattern_groups():
     pttrn = re.compile(r"hello (?P<prop>\w*) world")
     assert pttrn.groups == 1


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


def test_json_bytes_re_compile():
    import json
    assert isinstance(json.encoder.HAS_UTF8.pattern, bytes)
    assert json.encoder.HAS_UTF8.search(b"\x80") is not None
    assert json.encoder.HAS_UTF8.search(b"space") is None
    try:
        json.encoder.HAS_UTF8.search("\x80")
    except TypeError as e:
        pass
    else:
        assert False, "searching a bytes-pattern in a str did not raise"


def test_none_value():
    regex_find = re.compile(
        r"(//?| ==?)|([[]]+)").findall
    stream = iter([ (special,text)
                    for (special,text) in regex_find('[]')
                    if special or text ])
    n = next(stream)
    assert not n[0]
    assert str(n[0]) == ''


def test_find_all_none():
    import re
    pattern = re.compile(
        r"("
        r"'[^']*'|\"[^\"]*\"|"
        r"//?|"
        r"\(\)|"
        r"==?|"
        r"[/.*\[\]()@])|"
        r"([^/\[\]()@=\s]+)|"
        r"\s+"
    ).findall

    text = '//NameNode[not(@name)]'
    result = [
        ('//', ''),
        ('', 'NameNode'),
        ('[', ''),
        ('', 'not'),
        ('(', ''),
        ('@', ''),
        ('', 'name'),
        (')', ''),
        (']', ''),
    ]

    for i, r in enumerate(pattern(text)):
        assert result[i] == r


def test_sub_empty():
    result = re.sub(r'\d', lambda m: None, "f1")
    assert result == 'f'


class S(str):
    def __getitem__(self, index):
        return S(super().__getitem__(index))


class B(bytes):
    def __getitem__(self, index):
        return super().__getitem__(index)


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

    def checkPatternError(self, pattern, errmsg, pos=None):
        try:
            re.compile(pattern)
        except re.error:
            pass
        else:
            self.assertFalse(True, "expected exception")

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
        self.assertTypedEqual(re.sub('y', S('a'), S('xyz')), 'xaz')
        self.assertTypedEqual(re.sub(b'y', b'a', b'xyz'), b'xaz')
        self.assertTypedEqual(re.sub(b'y', B(b'a'), B(b'xyz')), b'xaz')
        self.assertTypedEqual(re.sub(b'y', bytearray(b'a'), bytearray(b'xyz')), b'xaz')
        self.assertTypedEqual(re.sub(b'y', memoryview(b'a'), memoryview(b'xyz')), b'xaz')
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
        self.assertEqual(re.sub('(?P<unk>x)', r'\g<unk>\g<unk>', 'xx'), 'xxxx')
        self.assertEqual(re.sub('(?P<unk>x)', r'\g<1>\g<1>', 'xx'), 'xxxx')

        self.assertEqual(re.sub('a', r'\t\n\v\r\f\a\b', 'a'), '\t\n\v\r\f\a\b')
        self.assertEqual(re.sub('a', '\t\n\v\r\f\a\b', 'a'), '\t\n\v\r\f\a\b')
        self.assertEqual(re.sub('a', '\t\n\v\r\f\a\b', 'a'),
                         (chr(9) + chr(10) + chr(11) + chr(13) + chr(12) + chr(7) + chr(8)))

        # The following behavior is correct w.r.t. Python 3.7. However, currently
        # the gate uses CPython 3.4.1 to validate the test suite,
        # which does not pass this test case, so we have to skip.
        # for c in 'cdehijklmopqsuwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ':
        #     with self.assertRaises(re.error):
        #         self.assertEqual(re.sub('a', '\\' + c, 'a'), '\\' + c)


        self.assertEqual(re.sub(r'^\s*', 'X', 'test'), 'Xtest')

    def test_bug_449964(self):
        # fails for group followed by other escape
        self.assertEqual(re.sub(r'(?P<unk>x)', r'\g<1>\g<1>\b', 'xx'), 'xx\bxx\b')

    def test_bug_449000(self):
        # Test for sub() on escaped characters
        self.assertEqual(re.sub(r'\r\n', r'\n', 'abc\r\ndef\r\n'),
                         'abc\ndef\n')
        self.assertEqual(re.sub('\r\n', r'\n', 'abc\r\ndef\r\n'),
                         'abc\ndef\n')
        self.assertEqual(re.sub(r'\r\n', '\n', 'abc\r\ndef\r\n'),
                         'abc\ndef\n')
        self.assertEqual(re.sub('\r\n', '\n', 'abc\r\ndef\r\n'),
                         'abc\ndef\n')

    def test_bug_1661(self):
        # Verify that flags do not get silently ignored with compiled patterns
        pattern = re.compile('.')
        self.assertRaises(ValueError, re.match, pattern, 'A', re.I)
        self.assertRaises(ValueError, re.search, pattern, 'A', re.I)
        self.assertRaises(ValueError, re.findall, pattern, 'A', re.I)
        self.assertRaises(ValueError, re.compile, pattern, re.I)

    def test_bug_3629(self):
        # A regex that triggered a bug in the sre-code validator
        re.compile("(?P<quote>)(?(quote))")

    def test_qualified_re_sub(self):
        self.assertEqual(re.sub('a', 'b', 'aaaaa'), 'bbbbb')
        self.assertEqual(re.sub('a', 'b', 'aaaaa', 1), 'baaaa')
        self.assertEqual(re.sub('a', 'b', 'aaaaa', count=1), 'baaaa')

    def test_bug_114660(self):
        self.assertEqual(re.sub(r'(\S)\s+(\S)', r'\1 \2', 'hello  there'),
                         'hello there')
    def test_bug_462270(self):
        # Test for empty sub() behaviour, see SF bug #462270
        # self.assertEqual(re.sub('x*', '-', 'abxd'), '-a-b-d-')
        self.assertEqual(re.sub('x+', '-', 'abxd'), 'ab-d')

    def test_symbolic_groups(self):
        re.compile(r'(?P<a>x)(?P=a)(?(a)y)')
        re.compile(r'(?P<a1>x)(?P=a1)(?(a1)y)')
        re.compile(r'(?P<a1>x)\1(?(1)y)')
        self.checkPatternError(r'(?P<a>)(?P<a>)',
                               "redefinition of group name 'a' as group 2; "
                               "was group 1")
        self.checkPatternError(r'(?Pxy)', 'unknown extension ?Px')
        self.checkPatternError(r'(?P<a>)(?P=a', 'missing ), unterminated name', 11)
        self.checkPatternError(r'(?P=', 'missing group name', 4)
        self.checkPatternError(r'(?P=)', 'missing group name', 4)
        self.checkPatternError(r'(?P=1)', "bad character in group name '1'", 4)
        self.checkPatternError(r'(?P=a)', "unknown group name 'a'")
        self.checkPatternError(r'(?P=a1)', "unknown group name 'a1'")
#         self.checkPatternError(r'(?P=a.)', "bad character in group name 'a.'", 4)
        self.checkPatternError(r'(?P<)', 'missing >, unterminated name', 4)
        self.checkPatternError(r'(?P<a', 'missing >, unterminated name', 4)
        self.checkPatternError(r'(?P<', 'missing group name', 4)
        self.checkPatternError(r'(?P<>)', 'missing group name', 4)
        self.checkPatternError(r'(?P<1>)', "bad character in group name '1'", 4)
#         self.checkPatternError(r'(?P<a.>)', "bad character in group name 'a.'", 4)
        self.checkPatternError(r'(?(', 'missing group name', 3)
        self.checkPatternError(r'(?())', 'missing group name', 3)
        self.checkPatternError(r'(?(a))', "unknown group name 'a'", 3)
        self.checkPatternError(r'(?(1a))', "bad character in group name '1a'", 3)
        self.checkPatternError(r'(?(a.))', "bad character in group name 'a.'", 3)
        # New valid/invalid identifiers in Python 3
        re.compile('(?P<µ>x)(?P=µ)(?(µ)y)')
        re.compile('(?P<𝔘𝔫𝔦𝔠𝔬𝔡𝔢>x)(?P=𝔘𝔫𝔦𝔠𝔬𝔡𝔢)(?(𝔘𝔫𝔦𝔠𝔬𝔡𝔢)y)')
        self.checkPatternError('(?P<©>x)', "bad character in group name '©'", 4)
        # Support > 100 groups.
        pat = '|'.join('x(?P<a%d>%x)y' % (i, i) for i in range(1, 200 + 1))
        pat = '(?:%s)(?(200)z|t)' % pat
        if sys.version_info.minor >= 6:
            self.checkPatternError(r'(?P<a>(?P=a))',
                                   "cannot refer to an open group", 10)
            self.checkPatternError(r'(?(-1))', "bad character in group name '-1'", 3)
            self.assertEqual(re.match(pat, 'xc8yz').span(), (0, 5))

    def test_re_subn(self):
        self.assertEqual(re.subn("(?i)b+", "x", "bbbb BBBB"), ('x x', 2))
        self.assertEqual(re.subn("b+", "x", "bbbb BBBB"), ('x BBBB', 1))
        self.assertEqual(re.subn("b+", "x", "xyz"), ('xyz', 0))
        self.assertEqual(re.subn("b*", "x", "xyz"), ('xxxyxzx', 4))
        self.assertEqual(re.subn("b*", "x", "xyz", 2), ('xxxyz', 2))
        self.assertEqual(re.subn("b*", "x", "xyz", count=2), ('xxxyz', 2))

    def test_re_split(self):
        for string in ":a:b::c", S(":a:b::c"):
            self.assertTypedEqual(re.split(":", string),
                                  ['', 'a', 'b', '', 'c'])
            self.assertTypedEqual(re.split(":+", string),
                                  ['', 'a', 'b', 'c'])
            self.assertTypedEqual(re.split("(:+)", string),
                                  ['', ':', 'a', ':', 'b', '::', 'c'])
        for string in (b":a:b::c", B(b":a:b::c"), bytearray(b":a:b::c"),
                       memoryview(b":a:b::c")):
            self.assertTypedEqual(re.split(b":", string),
                                  [b'', b'a', b'b', b'', b'c'])
            self.assertTypedEqual(re.split(b":+", string),
                                  [b'', b'a', b'b', b'c'])
            self.assertTypedEqual(re.split(b"(:+)", string),
                                  [b'', b':', b'a', b':', b'b', b'::', b'c'])
        # TODO not supported yet
        # for a, b, c in ("\xe0\xdf\xe7", "\u0430\u0431\u0432", "\U0001d49c\U0001d49e\U0001d4b5"):
        for a, b, c in ("\xe0\xdf\xe7", "\u0430\u0431\u0432"):
            string = ":%s:%s::%s" % (a, b, c)
            self.assertEqual(re.split(":", string), ['', a, b, '', c])
            self.assertEqual(re.split(":+", string), ['', a, b, c])
            self.assertEqual(re.split("(:+)", string),
                             ['', ':', a, ':', b, '::', c])

        self.assertEqual(re.split("(?::+)", ":a:b::c"), ['', 'a', 'b', 'c'])
        self.assertEqual(re.split("(:)+", ":a:b::c"),
                         ['', ':', 'a', ':', 'b', ':', 'c'])
        self.assertEqual(re.split("([b:]+)", ":a:b::c"),
                         ['', ':', 'a', ':b::', 'c'])
        self.assertEqual(re.split("(b)|(:+)", ":a:b::c"),
                         ['', None, ':', 'a', None, ':', '', 'b', None, '',
                          None, '::', 'c'])
        self.assertEqual(re.split("(?:b)|(?::+)", ":a:b::c"),
                         ['', 'a', '', '', 'c'])

        # TODO subtests not support yet
        # for sep, expected in [
        #    (':*', ['', 'a', 'b', 'c']),
        #    ('(?::*)', ['', 'a', 'b', 'c']),
        #    ('(:*)', ['', ':', 'a', ':', 'b', '::', 'c']),
        #    ('(:)*', ['', ':', 'a', ':', 'b', ':', 'c']),
        # ]:
        #    with self.subTest(sep=sep), self.assertWarns(FutureWarning):
        #       self.assertTypedEqual(re.split(sep, ':a:b::c'), expected)
        # for sep, expected in [
        #    ('', [':a:b::c']),
        #    (r'\b', [':a:b::c']),
        #    (r'(?=:)', [':a:b::c']),
        #    (r'(?<=:)', [':a:b::c']),
        # ]:
        #    with self.subTest(sep=sep), self.assertRaises(ValueError):
        #        self.assertTypedEqual(re.split(sep, ':a:b::c'), expected)

    def test_re_findall(self):
        self.assertEqual(re.findall(":+", "abc"), [])
        for string in "a:b::c:::d", S("a:b::c:::d"):
            self.assertTypedEqual(re.findall(":+", string),
                                  [":", "::", ":::"])
            self.assertTypedEqual(re.findall("(:+)", string),
                                  [":", "::", ":::"])
            self.assertTypedEqual(re.findall("(:)(:*)", string),
                                  [(":", ""), (":", ":"), (":", "::")])
        for string in (b"a:b::c:::d", B(b"a:b::c:::d"), bytearray(b"a:b::c:::d"),
                       memoryview(b"a:b::c:::d")):
            self.assertTypedEqual(re.findall(b":+", string),
                                  [b":", b"::", b":::"])
            self.assertTypedEqual(re.findall(b"(:+)", string),
                                  [b":", b"::", b":::"])
            self.assertTypedEqual(re.findall(b"(:)(:*)", string),
                                  [(b":", b""), (b":", b":"), (b":", b"::")])
        for x in ("\xe0", "\u0430", "\U0001d49c"):
            xx = x * 2
            xxx = x * 3
            string = "a%sb%sc%sd" % (x, xx, xxx)
            self.assertEqual(re.findall("%s+" % x, string), [x, xx, xxx])
            self.assertEqual(re.findall("(%s+)" % x, string), [x, xx, xxx])
            self.assertEqual(re.findall("(%s)(%s*)" % (x, x), string),
                             [(x, ""), (x, x), (x, xx)])

    def test_ignore_case_set(self):
        self.assertTrue(re.match(r'[19A]', 'A', re.I))
        self.assertTrue(re.match(r'[19a]', 'a', re.I))
        self.assertTrue(re.match(r'[19a]', 'A', re.I))
        self.assertTrue(re.match(r'[19A]', 'a', re.I))
        self.assertTrue(re.match(br'[19A]', b'A', re.I))
        self.assertTrue(re.match(br'[19a]', b'a', re.I))
        self.assertTrue(re.match(br'[19a]', b'A', re.I))
        self.assertTrue(re.match(br'[19A]', b'a', re.I))
        assert '\u212a'.lower() == 'k'  # 'K'
        self.assertTrue(re.match(r'[19K]', '\u212a', re.I))
        self.assertTrue(re.match(r'[19k]', '\u212a', re.I))
        self.assertTrue(re.match(r'[19\u212a]', 'K', re.I))
        self.assertTrue(re.match(r'[19\u212a]', 'k', re.I))
        if sys.version_info.minor >= 6:
            assert '\u017f'.upper() == 'S'  # 'ſ'
            self.assertTrue(re.match(r'[19S]', '\u017f', re.I))
            self.assertTrue(re.match(r'[19s]', '\u017f', re.I))
            self.assertTrue(re.match(r'[19\u017f]', 'S', re.I))
            self.assertTrue(re.match(r'[19\u017f]', 's', re.I))
        assert '\ufb05'.upper() == '\ufb06'.upper() == 'ST'  # 'ﬅ', 'ﬆ'
#         self.assertTrue(re.match(r'[19\ufb05]', '\ufb06', re.I))
#         self.assertTrue(re.match(r'[19\ufb06]', '\ufb05', re.I))

    def test_getattr(self):
        self.assertEqual(re.compile("(?i)(a)(b)").pattern, "(?i)(a)(b)")
        # TODO at the moment, we use slightly different default flags
        #self.assertEqual(re.compile("(?i)(a)(b)").flags, re.I | re.U)

        # TODO re-enable this test once TRegex provides this property
        #self.assertEqual(re.compile("(?i)(a)(b)").groups, 2)
        self.assertEqual(re.compile("(?i)(a)(b)").groupindex, {})
        self.assertEqual(re.compile("(?i)(?P<first>a)(?P<other>b)").groupindex,
                         {'first': 1, 'other': 2})

        self.assertEqual(re.match("(a)", "a").pos, 0)
        self.assertEqual(re.match("(a)", "a").endpos, 1)
        self.assertEqual(re.match("(a)", "a").string, "a")

        # TODO not yet supported
        #self.assertEqual(re.match("(a)", "a").regs, ((0, 1), (0, 1)))

        self.assertTrue(re.match("(a)", "a").re)

        # Issue 14260. groupindex should be non-modifiable mapping.
        p = re.compile(r'(?i)(?P<first>a)(?P<other>b)')
        self.assertEqual(sorted(p.groupindex), ['first', 'other'])
        self.assertEqual(p.groupindex['other'], 2)

        if sys.version_info.minor >= 6:
            with self.assertRaises(TypeError):
                p.groupindex['other'] = 0
        self.assertEqual(p.groupindex['other'], 2)

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
        assert "frac" in match.groupdict()
        assert match.groupdict()["frac"] == "1"


    def test_escape(self):
        self.assertEqual(re.escape(" ()"), "\\ \\(\\)")

    def test_finditer_empty_string(self):
        regex = re.compile(
            r"(//?| ==?)|([[]]+)")
        for m in regex.finditer(''):
            self.fail()


class PatternTest(unittest.TestCase):

    def test_search(self):
        # scans through string looking for the first location where this regular expression produces a match
        pattern = re.compile("o")
        match = pattern.search("dog")

        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), "o")
        self.assertEqual(match.start(), 1)
        self.assertEqual(match.end(), 2)
        self.assertEqual(match.string, "dog")
        self.assertEqual(match.re, pattern)


        # returns None if no position in the string matches the pattern
        pattern = re.compile("Z")
        match = pattern.search("dog")
        self.assertIsNone(match)


        # the optional second parameter pos gives an index in the string where the search is to start
        pattern = re.compile("o")

        match = pattern.search("dog", 1)
        self.assertEqual(match.group(0), "o")

        match = pattern.search("dog", 2)
        self.assertIsNone(match)


        # the optional parameter endpos limits how far the string will be searched
        pattern = re.compile("o")

        match = pattern.search("dog", 0, 2)
        self.assertEqual(match.group(0), "o")

        match = pattern.search("dog", 0, 1)
        self.assertIsNone(match)


        # returns None if pos >= endpos
        pattern = re.compile("o")

        match = pattern.search("dog", pos = 3, endpos = 0)
        self.assertIsNone(match)


        # accepts pos < 0 and treats it as 0
        pattern = re.compile("o")
        match = pattern.search("dog", -10)
        self.assertEqual(match.group(0), "o")


        # accepts endpos > length and treats it as length
        pattern = re.compile("o")
        match = pattern.search("dog", 0, 100)
        self.assertEqual(match.group(0), "o")


        # accepts pos > length and returns None
        pattern = re.compile("o")
        match = pattern.search("dog", 100)
        self.assertIsNone(match)


        # accepts a string as a "string" keyword argument
        pattern = re.compile("o")
        match = pattern.search(string = "dog")
        self.assertIsInstance(match, re.Match)


        # accepts start position as a "pos" keyword argument
        pattern = re.compile("o")
        match = pattern.search("dog", pos = 1)
        self.assertIsInstance(match, re.Match)


        # accepts end position as an "endpos" keyword argument
        pattern = re.compile("o")
        match = pattern.search("dog", endpos = 2)
        self.assertIsInstance(match, re.Match)


        # converts pos into an integer calling __index__ on it
        pattern = re.compile("o")
        match = pattern.search("dog", IntegerLike(1))
        self.assertEqual(match.group(0), "o")


        # converts endpos into an integer calling __index__ on it
        pattern = re.compile("o")
        match = pattern.search("dog", 0, IntegerLike(2))
        self.assertEqual(match.group(0), "o")


        # supports bytes-like source

        # bytes
        pattern = re.compile(b"dog")
        match = pattern.search(b"dog")
        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), b"dog")

        # bytearray
        pattern = re.compile(b"dog")
        match = pattern.search(bytearray(b"dog"))
        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), b"dog")


        # does not support mixing string and bytes-like object
        with self.assertRaisesRegex(TypeError, "cannot use a string pattern on a bytes-like object"):
            re.compile("dog").search(b"dog")
        with self.assertRaisesRegex(TypeError, "cannot use a bytes pattern on a string-like object"):
            re.compile(b"dog").search("dog")


        # doesn't support anything other than string or bytes
        with self.assertRaisesRegex(TypeError, "expected string or bytes-like object.*"):
            re.compile("a").search([])


    def test_match(self):
        # returns a corresponding Match if zero or more characters at the beginning of string match this regular expression
        pattern = re.compile("d")
        match = pattern.match("dog")

        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), "d")
        self.assertEqual(match.start(), 0)
        self.assertEqual(match.end(), 1)
        self.assertEqual(match.string, "dog")
        self.assertEqual(match.re, pattern)


        # returns None if the string does not match the pattern
        pattern = re.compile("A")
        match = pattern.match("dog")
        self.assertIsNone(match)


        # returns None if the string does match the pattern but not at the beginning of string
        pattern = re.compile("g")
        match = pattern.match("dog")
        self.assertIsNone(match)


        # the optional second parameter pos gives an index in the string where the search is to start
        pattern = re.compile("o")

        match = pattern.match("dog", 1)
        self.assertIsInstance(match, re.Match)

        match = pattern.match("dog", 2)
        self.assertIsNone(match)


        # the optional third parameter limits how far the string will be searched
        pattern = re.compile("og")

        match = pattern.match("dog", 1, 3)
        self.assertIsInstance(match, re.Match)

        match = pattern.match("dog", 1, 2)
        self.assertIsNone(match)


        # returns None if pos >= endpos
        pattern = re.compile("o")

        match = pattern.match("dog", pos = 1, endpos = 0)
        self.assertIsNone(match)


        # accepts pos < 0 and treats it as 0
        pattern = re.compile("d")
        match = pattern.match("dog", -10)
        self.assertEqual(match.group(0), "d")


        # accepts endpos > length and treats it as length
        pattern = re.compile("d")
        match = pattern.match("dog", 0, 100)
        self.assertEqual(match.group(0), "d")


        # accepts pos > length and returns None
        pattern = re.compile("d")
        match = pattern.match("dog", 100)
        self.assertIsNone(match)


        # accepts a string as a "string" keyword argument
        pattern = re.compile("d")
        match = pattern.match(string = "dog")
        self.assertIsInstance(match, re.Match)


        # accepts start position as a "pos" keyword argument
        pattern = re.compile("o")
        match = pattern.match("dog", pos = 1)
        self.assertIsInstance(match, re.Match)


        # accepts end position as an "endpos" keyword argument
        pattern = re.compile("og")
        match = pattern.match("dog", 1, endpos = 3)
        self.assertIsInstance(match, re.Match)


        # converts pos into an integer calling __index__ on it
        pattern = re.compile("o")
        match = pattern.match("dog", IntegerLike(1))
        self.assertIsInstance(match, re.Match)


        # converts endpos into an integer calling __index__ on it
        pattern = re.compile("o")
        match = pattern.match("dog", 1, IntegerLike(3))
        self.assertIsInstance(match, re.Match)


        # supports bytes-like source

        # bytes
        pattern = re.compile(b"dog")
        match = pattern.match(b"dog")
        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), b"dog")

        # bytearray
        pattern = re.compile(b"dog")
        match = pattern.match(bytearray(b"dog"))
        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), b"dog")


        # does not support mixing string and bytes-like object
        with self.assertRaisesRegex(TypeError, "cannot use a string pattern on a bytes-like object"):
            re.compile("dog").match(b"dog")
        with self.assertRaisesRegex(TypeError, "cannot use a bytes pattern on a string-like object"):
            re.compile(b"dog").match("dog")


        # doesn't support anything other than string or bytes
        with self.assertRaisesRegex(TypeError, "expected string or bytes-like object.*"):
            re.compile("a").match([])


    def test_fullmatch(self):
        # returns a corresponding Match if the whole string matches this regular expression
        pattern = re.compile("dog")
        match = pattern.fullmatch("dog")

        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), "dog")
        self.assertEqual(match.start(), 0)
        self.assertEqual(match.end(), 3)
        self.assertEqual(match.string, "dog")
        self.assertEqual(match.re, pattern)


        # returns None if the string does not match the pattern
        pattern = re.compile("dog")
        match = pattern.fullmatch(" dog ")
        self.assertIsNone(match)


        # the optional second parameter pos gives an index in the string where the search is to start
        pattern = re.compile("dog")

        match = pattern.fullmatch(" dog", 1)
        self.assertIsInstance(match, re.Match)

        match = pattern.fullmatch(" dog", 2)
        self.assertIsNone(match)


        # the optional parameter endpos limits how far the string will be searched
        pattern = re.compile("dog")

        match = pattern.fullmatch("dog ", 0, 3)
        self.assertIsInstance(match, re.Match)

        match = pattern.fullmatch("dog ", 0, 4)
        self.assertIsNone(match)


        # returns None if pos >= endpos
        pattern = re.compile("o")

        match = pattern.fullmatch(" dog", pos = 1, endpos = 0)
        self.assertIsNone(match)


        # accepts pos < 0 and treats it as 0
        pattern = re.compile("dog")
        match = pattern.fullmatch("dog", -10)
        self.assertEqual(match.group(0), "dog")


        # accepts endpos > length and treats it as length
        pattern = re.compile("dog")
        match = pattern.fullmatch("dog", 0, 100)
        self.assertEqual(match.group(0), "dog")


        # accepts pos > length and returns None
        pattern = re.compile("dog")
        match = pattern.fullmatch("dog", 100)
        self.assertIsNone(match)


        # accepts a string as a "string" keyword argument
        pattern = re.compile("dog")
        match = pattern.fullmatch(string = "dog")
        self.assertIsInstance(match, re.Match)


        # accepts start position as a "pos" keyword argument
        pattern = re.compile("dog")
        match = pattern.fullmatch(" dog", pos = 1)
        self.assertIsInstance(match, re.Match)


        # accepts end position as an "endpos" keyword argument
        pattern = re.compile("dog")
        match = pattern.fullmatch("dog ", endpos = 3)
        self.assertIsInstance(match, re.Match)


        # converts pos into an integer calling __index__ on it
        pattern = re.compile("dog")
        match = pattern.fullmatch(" dog", IntegerLike(1))
        self.assertIsInstance(match, re.Match)


        # converts endpos into an integer calling __index__ on it
        pattern = re.compile("dog")
        match = pattern.fullmatch("dog ", 0, IntegerLike(3))
        self.assertIsInstance(match, re.Match)


        # supports bytes-like source

        # bytes
        pattern = re.compile(b"dog")
        match = pattern.fullmatch(b"dog")
        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), b"dog")

        # bytearray
        pattern = re.compile(b"dog")
        match = pattern.fullmatch(bytearray(b"dog"))
        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), b"dog")


        # does not support mixing string and bytes-like object
        with self.assertRaisesRegex(TypeError, "cannot use a string pattern on a bytes-like object"):
            re.compile("dog").fullmatch(b"dog")
        with self.assertRaisesRegex(TypeError, "cannot use a bytes pattern on a string-like object"):
            re.compile(b"dog").fullmatch("dog")


        # doesn't support anything other than string or bytes
        with self.assertRaisesRegex(TypeError, "expected string or bytes-like object.*"):
            re.compile('a').fullmatch([])


    def test_split(self):
        # splits string by the occurrences of pattern
        pattern = re.compile(r'\W+')
        result = pattern.split('Words, words, words.')
        self.assertEqual(result, ['Words', 'words', 'words', ''])

        #  if maxsplit is nonzero, at most maxsplit splits occur, and the remainder of the string is returned as the final element of the list.
        pattern = re.compile(r'\W+')
        result = pattern.split('Words, words, words.', 1)
        self.assertEqual(result, ['Words', 'words, words.'])


        # converts maxplit into an integer calling __index__ on it
        pattern = re.compile(r'\W+')
        result = pattern.split('Words, words, words.', IntegerLike(1))
        self.assertEqual(result, ['Words', 'words, words.'])


        # accepts a string as a "string" keyword argument
        pattern = re.compile(r'\W+')
        result = pattern.split(string = 'Words, words, words.')
        self.assertIsInstance(result, list)


        # accepts the upper limit of split parts as a "maxsplit" keyword argument
        pattern = re.compile(r'\W+')
        result = pattern.split('Words, words, words.', maxsplit = 1)
        self.assertIsInstance(result, list)


        # supports bytes-like source

        # bytes
        pattern = re.compile(br'\W+')
        result = pattern.split(b'Words, words, words.')
        self.assertEqual(result, [b'Words', b'words', b'words', b''])

        # bytearray
        pattern = re.compile(br'\W+')
        result = pattern.split(bytearray(b'Words, words, words.'))
        self.assertEqual(result, [b'Words', b'words', b'words', b''])


        # does not support mixing string and bytes-like object
        with self.assertRaisesRegex(TypeError, "cannot use a string pattern on a bytes-like object"):
            re.compile(r'\W+').split(b'Words, words, words.')
        with self.assertRaisesRegex(TypeError, "cannot use a bytes pattern on a string-like object"):
            re.compile(br'\W+').split('Words, words, words.')


        # doesn't support anything other than string or bytes
        with self.assertRaisesRegex(TypeError, "expected string or bytes-like object.*"):
            re.compile("a").split([])


    def test_findall(self):
        # returns all non-overlapping matches of pattern in string, as a list of strings or tuples
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest')
        self.assertEqual(result, ['foot', 'fell', 'fastest'])


        # returns a list of strings matching a group if there is exactly one group
        pattern = re.compile(r'\b(f[a-z]*)')
        result = pattern.findall('which foot or hand fell fastest')
        self.assertEqual(result, ['foot', 'fell', 'fastest'])


        # returns a list of tuples of strings matching the groups if multiple groups are present
        pattern = re.compile(r'\b(f)([a-z]*)')
        result = pattern.findall('which foot or hand fell fastest')
        self.assertEqual(result, [('f', 'oot'), ('f', 'ell'), ('f', 'astest')])


        # the optional second parameter pos gives an index in the string where the search is to start
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest', 7)
        self.assertEqual(result, ['fell', 'fastest'])


        # the optional parameter endpos limits how far the string will be searched
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest', 0, 23)
        self.assertEqual(result, ['foot', 'fell'])


        # converts pos into an integer calling __index__ on it
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest', IntegerLike(7))
        self.assertEqual(result, ['fell', 'fastest'])


        # converts endpos into an integer calling __index__ on it
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest', 0, IntegerLike(23))
        self.assertEqual(result, ['foot', 'fell'])


        # returns [] if pos >= endpos
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest', pos = 1, endpos = 0)
        self.assertEqual(result, [])


        # accepts pos < 0 and treats it as 0
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest', -10)
        self.assertEqual(result, ['foot', 'fell', 'fastest'])


        # accepts endpos > length and treats it as length
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest', 0, 100)
        self.assertEqual(result, ['foot', 'fell', 'fastest'])


        # accepts pos > length and returns []
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest', 100)
        self.assertEqual(result, [])


        # accepts a string as a "string" keyword argument
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall(string = 'which foot or hand fell fastest')
        self.assertIsInstance(result, list)


        # accepts start position as a "pos" keyword argument
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest', pos = 7)
        self.assertIsInstance(result, list)


        # accepts end position as an "endpos" keyword argument
        pattern = re.compile(r'\bf[a-z]*')
        result = pattern.findall('which foot or hand fell fastest', endpos = 23)
        self.assertIsInstance(result, list)


        # supports bytes-like source

        # bytes
        pattern = re.compile(br'\bf[a-z]*')
        result = pattern.findall(b'which foot or hand fell fastest')
        self.assertEqual(result, [b'foot', b'fell', b'fastest'])

        # bytearray
        pattern = re.compile(br'\bf[a-z]*')
        result = pattern.findall(bytearray(b'which foot or hand fell fastest'))
        self.assertEqual(result, [b'foot', b'fell', b'fastest'])


        # does not support mixing string and bytes-like object
        with self.assertRaisesRegex(TypeError, "cannot use a string pattern on a bytes-like object"):
            re.compile(r'\bf[a-z]*').findall(b'which foot or hand fell fastest')
        with self.assertRaisesRegex(TypeError, "cannot use a bytes pattern on a string-like object"):
            re.compile(br'\bf[a-z]*').findall('which foot or hand fell fastest')


        # doesn't support anything other than string or bytes
        with self.assertRaisesRegex(TypeError, "expected string or bytes-like object.*"):
            re.compile('a').findall([])


    def test_finditer(self):
        from typing import Iterable

        # returns an iterator yielding Match objects over all non-overlapping matches for the RE pattern in string
        pattern = re.compile(r'\b[a-z]*')
        result = pattern.finditer('which foot or hand fell fastest')
        self.assertIsInstance(result, Iterable)

        result_list = list(result)
        self.assertTrue(all(isinstance(el, re.Match) for el in result_list))
        self.assertEqual(list(m[0] for m in result_list), ['which', '', 'foot', '', 'or', '', 'hand', '', 'fell', '', 'fastest', ''])


        # the optional second parameter pos gives an index in the string where the search is to start
        pattern = re.compile(r'\b[a-z]*')
        result = pattern.finditer('which foot or hand fell fastest', 7)
        self.assertEqual(list(m[0] for m in result), ['', 'or', '', 'hand', '', 'fell', '', 'fastest', ''])


        # FIXME: GraalPy does not return the terminating ''
        # # the optional parameter endpos limits how far the string will be searched
        # pattern = re.compile(r'\b[a-z]*')
        # result = pattern.finditer('which foot or hand fell fastest', 0, 23)
        # self.assertEqual(list(m[0] for m in result), ['which', '', 'foot', '', 'or', '', 'hand', '', 'fell', ''])


        # converts pos into an integer calling __index__ on it
        pattern = re.compile(r'\b[a-z]*')
        result = pattern.finditer('which foot or hand fell fastest', IntegerLike(7))
        self.assertEqual(list(m[0] for m in result), ['', 'or', '', 'hand', '', 'fell', '', 'fastest', ''])


        # FIXME: GraalPy does not return the terminating ''
        # # converts endpos into an integer calling __index__ on it
        # pattern = re.compile(r'\b[a-z]*')
        # result = pattern.finditer('which foot or hand fell fastest', 0, IntegerLike(23))
        # self.assertEqual(list(m[0] for m in result), ['which', '', 'foot', '', 'or', '', 'hand', '', 'fell', ''])


        # returns an empty iterator if pos >= endpos
        pattern = re.compile(r'\b[a-z]*')
        result = pattern.finditer('which foot or hand fell fastest', pos = 1, endpos = 0)
        self.assertEqual(list(result), [])


        # accepts pos < 0 and treats it as 0
        pattern = re.compile(r'\b[a-z]*')
        result = pattern.finditer('which foot or hand fell fastest', -10)
        self.assertEqual(list(m[0] for m in result), ['which', '', 'foot', '', 'or', '', 'hand', '', 'fell', '', 'fastest', ''])


        # accepts endpos > length and treats it as length
        pattern = re.compile(r'\b[a-z]*')
        result = pattern.finditer('which foot or hand fell fastest', 0, 100)
        self.assertEqual(list(m[0] for m in result), ['which', '', 'foot', '', 'or', '', 'hand', '', 'fell', '', 'fastest', ''])


        # accepts pos > length and returns []
        pattern = re.compile(r'\b[a-z]*')
        result = pattern.finditer('which foot or hand fell fastest', 100)
        self.assertEqual(list(m[0] for m in result), [''])


        # accepts a string as a "string" keyword argument
        pattern = re.compile(r'\b[a-z]*')
        result = pattern.finditer(string = 'which foot or hand fell fastest')
        self.assertIsInstance(result, Iterable)


        # accepts start position as a "pos" keyword argument
        pattern = re.compile(r'\b[a-z]*')
        result = pattern.finditer('which foot or hand fell fastest', pos = 7)
        self.assertIsInstance(result, Iterable)


        # accepts end position as an "endpos" keyword argument
        pattern = re.compile(r'\b[a-z]*')
        result = pattern.finditer('which foot or hand fell fastest', endpos = 23)
        self.assertIsInstance(result, Iterable)


        # supports bytes-like source

        # bytes
        pattern = re.compile(br'\b[a-z]*')
        result = pattern.finditer(b'which foot or hand fell fastest')
        self.assertIsInstance(result, Iterable)
        self.assertEqual(list(m[0] for m in result), [b'which', b'', b'foot', b'', b'or', b'', b'hand', b'', b'fell', b'', b'fastest', b''])

        # bytearray
        pattern = re.compile(br'\b[a-z]*')
        result = pattern.finditer(bytearray(b'which foot or hand fell fastest'))
        self.assertIsInstance(result, Iterable)
        self.assertEqual(list(m[0] for m in result), [b'which', b'', b'foot', b'', b'or', b'', b'hand', b'', b'fell', b'', b'fastest', b''])


        # does not support mixing string and bytes-like object
        with self.assertRaisesRegex(TypeError, "cannot use a string pattern on a bytes-like object"):
            re.compile(r'\bf[a-z]*').finditer(b'which foot or hand fell fastest')
        with self.assertRaisesRegex(TypeError, "cannot use a bytes pattern on a string-like object"):
            re.compile(br'\bf[a-z]*').finditer('which foot or hand fell fastest')


        # doesn't support anything other than string or bytes
        with self.assertRaisesRegex(TypeError, "expected string or bytes-like object.*"):
            re.compile('a').finditer([])


    def test_sub(self):
        # returns the string obtained by replacing all the non-overlapping occurrences of pattern in
        # string by the replacement repl
        pattern = re.compile('x+')
        result = pattern.sub('-', 'abxdxx')
        self.assertEqual(result, 'ab-d-')

        # string is returned unchanged if the pattern isn’t found
        pattern = re.compile('x+')
        string = 'abd'
        result = pattern.sub('-', string)
        self.assertIs(result, string)

        # backreferences are replaced with the substring matched by the corresponding group in the pattern
        pattern = re.compile('(x)(.)')
        result = pattern.sub(r'[\2]', 'axbcxd')
        self.assertEqual(result, 'a[b]c[d]')


        # if repl is a function it is called for every non-overlapping occurrence of pattern
        def repl(matchobj):
            if matchobj.group(0) == '-':
                return ' '
            else:
                return '-'

        pattern = re.compile(r'\W')
        result = pattern.sub(repl, 'a-b c-d')
        self.assertEqual(result, 'a b-c d')

        # the optional argument count is the maximum number of pattern occurrences to be replaced
        pattern = re.compile('x')
        result = pattern.sub('-', 'axbxcxd', 2)
        self.assertEqual(result, 'a-b-cxd')

        # count must be a non-negative integer otherwise no substitution is performed
        pattern = re.compile('x')
        result = pattern.sub('-', 'axbxcxd', -2)
        self.assertEqual(result, 'axbxcxd')

        # if zero all occurrences will be replaced.
        pattern = re.compile('x')
        result = pattern.sub('-', 'axbxcxd', 0)
        self.assertEqual(result, 'a-b-c-d')


        # converts count into an integer calling __index__ on it
        pattern = re.compile('x')
        result = pattern.sub('-', 'axbxcxd', IntegerLike(2))
        self.assertEqual(result, 'a-b-cxd')


        # accepts a replacement string as a "repl" keyword argument
        pattern = re.compile('x+')
        result = pattern.sub(repl = '-', string = 'abxdxx')
        self.assertEqual(result, 'ab-d-')


        # accepts a string as a "string" keyword argument
        pattern = re.compile('x+')
        result = pattern.sub('-', string = 'abxdxx')
        self.assertEqual(result, 'ab-d-')


        # accepts an upper limit as a "count" keyword argument
        pattern = re.compile('x')
        result = pattern.sub('-', 'axbxcxd', count = 2)
        self.assertEqual(result, 'a-b-cxd')


        # supports bytes-like source

        # bytes
        pattern = re.compile(b'x+')
        result = pattern.sub(b'-', b'abxdxx')
        self.assertEqual(result, b'ab-d-')

        # bytearray
        pattern = re.compile(b'x+')
        result = pattern.sub(b'-', bytearray(b'abxdxx'))
        self.assertEqual(result, b'ab-d-')


        # does not support mixing string and bytes-like object
        with self.assertRaisesRegex(TypeError, "cannot use a string pattern on a bytes-like object"):
            re.compile('x+').sub('-', b'abxdxx')
        with self.assertRaises(TypeError):
            re.compile('x+').sub(b'-', 'abxdxx')
        with self.assertRaisesRegex(TypeError, "cannot use a bytes pattern on a string-like object"):
            re.compile(b'x+').sub(b'-', 'abxdxx')
        with self.assertRaises(TypeError):
            re.compile(b'x+').sub('-', b'abxdxx')


    def test_subn(self):
        # returns a tuple (new_string, number_of_subs_made) where the string obtained by replacing
        # all the non-overlapping occurrences of pattern in string by the replacement repl
        pattern = re.compile('x+')
        result = pattern.subn('-', 'abxdxx')
        self.assertEqual(result, ('ab-d-', 2))

        # string is returned unchanged if the pattern isn’t found
        pattern = re.compile('x+')
        string = 'abd'
        result = pattern.subn('-', string)
        self.assertIs(result[0], string)
        self.assertEqual(result[1], 0)

        # backreferences are replaced with the substring matched by the corresponding group in the pattern
        pattern = re.compile('(x)(.)')
        result = pattern.subn(r'[\2]', 'axbcxd')
        self.assertEqual(result, ('a[b]c[d]', 2))


        # if repl is a function it is called for every non-overlapping occurrence of pattern
        def repl(match):
            if match.group(0) == '-':
                return ' '
            else:
                return '-'

        pattern = re.compile(r'\W')
        result = pattern.subn(repl, 'a-b c-d')
        self.assertEqual(result, ('a b-c d', 3))

        # the optional argument count is the maximum number of pattern occurrences to be replaced
        pattern = re.compile('x')
        result = pattern.subn('-', 'axbxcxd', count = 2)
        self.assertEqual(result, ('a-b-cxd', 2))

        # count must be a non-negative integer otherwise no substitution is performed
        pattern = re.compile('x')
        result = pattern.subn('-', 'axbxcxd', count = -2)
        self.assertEqual(result, ('axbxcxd', 0))

        # if count = zero then all occurrences will be replaced
        pattern = re.compile('x')
        result = pattern.subn('-', 'axbxcxd', count = 0)
        self.assertEqual(result, ('a-b-c-d', 3))


        # converts count into an integer calling __index__ on it
        pattern = re.compile('x')
        result = pattern.subn('-', 'axbxcxd', count = IntegerLike(2))
        self.assertEqual(result, ('a-b-cxd', 2))


        # accepts a replacement string as a "repl" keyword argument
        pattern = re.compile('x+')
        result = pattern.subn(repl = '-', string = 'abxdxx')
        self.assertIsInstance(result, tuple)


        # accepts a string as a "string" keyword argument
        pattern = re.compile('x+')
        result = pattern.subn('-', string = 'abxdxx')
        self.assertIsInstance(result, tuple)


        # accepts an upper limit as a "count" keyword argument
        pattern = re.compile('x')
        result = pattern.subn('-', 'axbxcxd', count = 2)
        self.assertIsInstance(result, tuple)


        # supports bytes-like source

        # bytes
        pattern = re.compile(b'x+')
        result = pattern.subn(b'-', b'abxdxx')
        self.assertEqual(result, (b'ab-d-', 2))

        # bytearray
        pattern = re.compile(b'x+')
        result = pattern.subn(b'-', bytearray(b'abxdxx'))
        self.assertEqual(result, (b'ab-d-', 2))


        # does not support mixing string and bytes-like object
        with self.assertRaisesRegex(TypeError, "cannot use a string pattern on a bytes-like object"):
            re.compile('x+').subn('-', b'abxdxx')
        with self.assertRaises(TypeError):
            re.compile('x+').subn(b'-', 'abxdxx')
        with self.assertRaisesRegex(TypeError, "cannot use a bytes pattern on a string-like object"):
            re.compile(b'x+').subn(b'-', 'abxdxx')
        with self.assertRaises(TypeError):
            re.compile(b'x+').subn('-', b'abxdxx')


    def test_flags(self):
        # returns regex matching flags, both explicit and inline ones
        pattern = re.compile('x', re.IGNORECASE | re.MULTILINE)
        self.assertEqual(pattern.flags, re.IGNORECASE | re.MULTILINE | re.UNICODE)

        # check all the inline flags
        self.assertEqual(re.compile('(?a)').flags, re.ASCII)
        self.assertEqual(re.compile('(?i)').flags, re.IGNORECASE | re.UNICODE)
        self.assertEqual(re.compile(b'(?L)').flags, re.LOCALE)
        self.assertEqual(re.compile('(?m)').flags, re.MULTILINE | re.UNICODE)
        self.assertEqual(re.compile('(?s)').flags, re.DOTALL | re.UNICODE)
        self.assertEqual(re.compile('(?u)').flags, re.UNICODE)
        self.assertEqual(re.compile('(?x)').flags, re.VERBOSE | re.UNICODE)


    def test_groups(self):
        # returns number of capturing groups in the pattern
        pattern = re.compile('(a)(b)(c)')
        self.assertEqual(pattern.groups, 3)


    def test_groupindex(self):
        # returns a dictionary mapping any symbolic group names defined by (?P<id>) to group numbers
        pattern = re.compile('(?P<a>.)(?P<b>.)(?P<c>.)')
        self.assertEqual(pattern.groupindex, {'a': 1, 'b': 2, 'c': 3})

        # returns an instance of types.MappingProxyType
        import types
        pattern = re.compile('(?P<a>.)(?P<b>.)(?P<c>.)')
        self.assertIsInstance(pattern.groupindex, types.MappingProxyType)

        # returns an empty dictionary if there are no any named capturing group
        pattern = re.compile('(.)(.)(.)')
        self.assertEqual(pattern.groupindex, {})

        # returns an empty dictionary if there are no any capturing group
        pattern = re.compile('abc')
        self.assertEqual(pattern.groupindex, {})


    def test_pattern(self):
        # returns the pattern string from which the pattern object was compiled
        string = '(a)(b)(c)'
        pattern = re.compile(string)
        self.assertIs(pattern.pattern, string)


    def test_repr(self):
        # without explicit flags
        pattern = re.compile('abc')
        self.assertEqual(repr(pattern), "re.compile('abc')")

        # with a single flag
        pattern = re.compile('abc', re.ASCII)
        self.assertEqual(repr(pattern), "re.compile('abc', re.ASCII)")

        # with multiple flags
        pattern = re.compile('abc', re.ASCII | re.VERBOSE)
        self.assertEqual(repr(pattern), "re.compile('abc', re.VERBOSE|re.ASCII)")

        # with unknown flag
        pattern = re.compile('abc', 512)
        self.assertEqual(repr(pattern), "re.compile('abc', 0x200)")


    def test_str(self):
        pattern = re.compile('abc')
        self.assertEqual(str(pattern), "re.compile('abc')")

        # with a single flag
        pattern = re.compile('abc', re.ASCII)
        self.assertEqual(str(pattern), "re.compile('abc', re.ASCII)")

        # with multiple flags
        pattern = re.compile('abc', re.ASCII | re.VERBOSE)
        self.assertEqual(str(pattern), "re.compile('abc', re.VERBOSE|re.ASCII)")

        # with unknown flag
        pattern = re.compile('abc', 512)
        self.assertEqual(str(pattern), "re.compile('abc', 0x200)")


    def test_subclassing(self):
        with self.assertRaisesRegex(TypeError, "type 're.Pattern' is not an acceptable base type"):
            class A(re.Pattern):
                pass


    def test_hash(self):
        # returns integer number
        pattern = re.compile('abc')
        self.assertIsInstance(hash(pattern), int)

        # returns the same value for equal patterns
        self.assertEqual(hash(re.compile('abc')), hash(re.compile('abc')))


    def test_comparison(self):
        # str

        # supports #==
        self.assertTrue(re.compile('abc') == re.compile('abc'))

        # supports #!=
        self.assertTrue(re.compile('abc') != re.compile('abcd'))
        self.assertTrue(re.compile('abc') != re.compile('abc', re.ASCII))
        self.assertTrue(re.compile('abc') != re.compile(b'abc'))


        # bytes

        # supports #==
        self.assertTrue(re.compile(b'abc') == re.compile(b'abc'))

        # supports #!=
        self.assertTrue(re.compile(b'abc') != re.compile(b'abcd'))
        self.assertTrue(re.compile(b'abc') != re.compile(b'abc', re.ASCII))
        self.assertTrue(re.compile(b'abc') != re.compile('abc'))


        # does not support <, >, <=, >=
        with self.assertRaisesRegex(TypeError, "'>' not supported between instances of 're.Pattern' and 're.Pattern'"):
            re.compile('abc') > re.compile('abc')
        with self.assertRaisesRegex(TypeError, "'>=' not supported between instances of 're.Pattern' and 're.Pattern'"):
            re.compile('abc') >= re.compile('abc')
        with self.assertRaisesRegex(TypeError, "'<' not supported between instances of 're.Pattern' and 're.Pattern'"):
            re.compile('abc') < re.compile('abc')
        with self.assertRaisesRegex(TypeError, "'<=' not supported between instances of 're.Pattern' and 're.Pattern'"):
            re.compile('abc') <= re.compile('abc')


    def test_scanner(self):
        # returns an object
        pattern = re.compile('abc')
        self.assertIsNotNone(pattern.scanner('abc'))

        # accepts optional arguments pos, endpos
        pattern = re.compile('abc')
        self.assertIsNotNone(pattern.scanner('abc', 0, 4))

        # accepts keyword arguments string, pos, and endpos
        pattern = re.compile('abc')
        self.assertIsNotNone(pattern.scanner(string = 'abc', pos = 0, endpos = 4))

        # does not support mixing string and bytes-like object
        pattern = re.compile("abc")
        with self.assertRaisesRegex(TypeError, "cannot use a string pattern on a bytes-like object"):
            pattern.scanner(b"abc")

        pattern = re.compile(b"abc")
        with self.assertRaisesRegex(TypeError, "cannot use a bytes pattern on a string-like object"):
            pattern.scanner("abc")


class MatchTest(unittest.TestCase):
    def test_expand(self):
        # any backslash escapes in a template string are processed
        match = re.match("a", "abc")
        self.assertEqual(match.expand(r"\n"), "\n")

        # unknown escapes of ASCII letters are reserved for future use and treated as errors
        match = re.match("a", "abc")
        with self.assertRaisesRegex(re.error, r"bad escape \\Z at position 0"):
            match.expand(r"\Z")

        # other unknown escapes such as \& are left alone
        match = re.match("a", "abc")
        self.assertEqual(match.expand(r"\&"), r"\&")

        # numeric backreferences (\<n>) are replaced with the substring matched by group number in the pattern
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.expand(r"__\1 \2 \3__"), "__a b c__")

        # named backreferences (\g<name>) are replaced with the substring matched by group name in the pattern
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match.expand(r"__\g<G1> \g<G2> \g<G3>__"), "__a b c__")

        # named backreferences (\g<n>) are replaced with the substring matched by group number in the pattern
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match.expand(r"__\g<1> \g<2> \g<3>__"), "__a b c__")

        # backreference \g<0> is replaced by the entire match
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.expand(r"__\g<0>__"), "__abc__")

        # numeric backreference \0 is replaced with \x00 character
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.expand(r"__\0__"), "__\x00__")

        # raises error when number exceeds the number of groups
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(re.error, "invalid group reference 5 at position 1"):
            match.expand(r"\5")

        # unmatched groups are replaced with an empty string
        match = re.match("(a)(b)(c)?", "abd")
        self.assertEqual(match.expand(r"__\1 \2 \3__"), "__a b __")

        # unmatched named groups are replaced with an empty string
        match = re.match("(?P<G1>abc)|(?P<G2>xyz)", "abc")
        self.assertEqual(match.expand(r"__\g<G1> \g<G2>__"), "__abc __")

        # GraalPy requires a template argument to be a hashable object (that's to have __hash__() function)
        # # accepts a bytes-like object as a template
        # match = re.match("a", "abc")
        # self.assertEqual(match.expand(br"\n"), b"\n")
        # self.assertEqual(match.expand(bytearray(br"\n")), b"\n")

        # doesn't expect anything other than bytes-like object or string
        match = re.match("a", "abc")
        with self.assertRaisesRegex(TypeError, "decoding to str: need a bytes-like object, range found"):
            match.expand(range(1, 2))


    # Synchronized with test___getitem__
    def test_group(self):
        # if there is a single argument, the result is a single string
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.group(1), "a")

        # if there are multiple arguments, the result is a tuple with one item per argument
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.group(1, 2), ("a", "b"))

        # without arguments, group number defaults to zero (the whole matching string is returned)
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.group(), "abc")

        # returns the whole matching string when given 0
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.group(0), "abc")

        # if the Nth argument is zero, the corresponding returned value is the entire matching string
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.group(1, 2, 3, 0), ("a", "b", "c", "abc"))

        # raises IndexError exception when given negative group number
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.group(-1)

        # raises IndexError exception when given group number larger than the number of groups defined in the pattern
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.group(10)

        # if a group is contained in a part of the pattern that did not match, the corresponding result is None
        match = re.match("(a)(b)(c)?", "abd")
        self.assertEqual(match.group(1, 2, 3), ("a", "b", None))

        # if a group is contained in a part of the pattern that matched multiple times, the last match is returned.
        match = re.match(r"(.)+", "abc")
        self.assertEqual(match.group(1), "c")

        # accepts group names
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match.group("G1", "G2", "G3"), ("a", "b", "c"))

        # if a string argument is not used as a group name in the pattern, an IndexError exception is raised.
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.group("G4")

        # named groups can also be referred to by their index
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match.group(1, 2, 3), ("a", "b", "c"))

        # raises IndexError exception when given argument is not integer or string
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.group(range(1, 2))

        # supports bytes-like source
        # bytes
        match = re.match(b"(a)(b)(c)", b"abc")
        self.assertIsInstance(match.group(1), bytes)
        self.assertEqual(match.group(1), b"a")
        # bytearray
        match = re.match(b"(a)(b)(c)", bytearray(b"abc"))
        self.assertIsInstance(match.group(1), bytes)
        self.assertEqual(match.group(1), b"a")

        # supports changing source's length
        source = bytearray(b'abcdefgh')
        match = re.search(b'[a-h]+', source)
        self.assertEqual(match.group(), b'abcdefgh')
        source[:] = b'xyz'
        self.assertEqual(match.group(), b'xyz')


    # Synchronized with test_group
    def test_getitem(self):
        # returns a matching substring for a given number
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match[1], "a")

        # returns the whole matching string when given 0
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match[0], "abc")

        # raises IndexError exception when given negative group number
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match[-1]

        # raises IndexError exception when given group number larger than the number of groups defined in the pattern
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match[10]

        # if a group is contained in a part of the pattern that did not match, the corresponding result is None
        match = re.match("(a)(b)(c)?", "abd")
        self.assertIsNone(match[3])

        # if a group is contained in a part of the pattern that matched multiple times, the last match is returned.
        match = re.match(r"(.)+", "abc")
        self.assertEqual(match[1], "c")

        # accepts group names
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match["G1"], "a")

        # if a string argument is not used as a group name in the pattern, an IndexError exception is raised.
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match["G4"]

        # named groups can also be referred to by their index
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match[1], "a")

        # raises IndexError exception when given argument is not integer or string
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match[range(1, 2)]

        # supports bytes-like source
        # bytes
        match = re.match(b"(a)(b)(c)", b"abc")
        self.assertIsInstance(match[1], bytes)
        self.assertEqual(match[1], b"a")
        # bytearray
        match = re.match(b"(a)(b)(c)", bytearray(b"abc"))
        self.assertIsInstance(match[1], bytes)
        self.assertEqual(match[1], b"a")


    def test_groups(self):
        # returns a tuple containing all the subgroups of the match
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.groups(), ("a", "b", "c"))

        # if a group did not match, the corresponding result is None
        match = re.match("(a)(b)(c)?", "abd")
        self.assertEqual(match.groups(), ("a", "b", None))

        # if a group did not match and an argument given, the corresponding result is the argument
        match = re.match("(a)(b)(c)?", "abd")
        self.assertEqual(match.groups("?"), ("a", "b", "?"))

        # accepts default value as a "default" keyword argument
        match = re.match("(a)(b)(c)?", "abd")
        self.assertEqual(match.groups(default = "?"), ("a", "b", "?"))

        # supports bytes-like source
        # bytes
        match = re.match(b"(a)(b)(c)", b"abc")
        self.assertEqual(match.groups(), (b"a", b"b", b"c"))
        self.assertIsInstance(match.groups()[0], bytes)
        # bytearray
        match = re.match(b"(a)(b)(c)", bytearray(b"abc"))
        self.assertEqual(match.groups(), (b"a", b"b", b"c"))
        self.assertIsInstance(match.groups()[0], bytes)


    def test_groupdict(self):
        # returns a dictionary containing all the named subgroups of the match
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match.groupdict(), {"G1": "a", "G2": "b", "G3": "c"})

        # returns None for unmatched groups
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)?", "ab")
        self.assertEqual(match.groupdict(), {"G1": "a", "G2": "b", "G3": None})

        # returns an argument for unmatched groups when given argument
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)?", "ab")
        self.assertEqual(match.groupdict("?"), {"G1": "a", "G2": "b", "G3": "?"})

        # accepts default value as a "default" keyword argument
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)?", "ab")
        self.assertEqual(match.groupdict(default = "?"), {"G1": "a", "G2": "b", "G3": "?"})

        # supports bytes-like source
        # bytes
        match = re.match(b"(?P<G1>a)(?P<G2>b)(?P<G3>c)", b"abc")
        self.assertEqual(match.groupdict(), {"G1": b"a", "G2": b"b", "G3": b"c"})
        self.assertIsInstance(list(match.groupdict().keys())[0], str)
        self.assertIsInstance(list(match.groupdict().values())[0], bytes)
        # bytearray
        match = re.match(b"(?P<G1>a)(?P<G2>b)(?P<G3>c)", bytearray(b"abc"))
        self.assertEqual(match.groupdict(), {"G1": b"a", "G2": b"b", "G3": b"c"})
        self.assertIsInstance(list(match.groupdict().keys())[0], str)
        self.assertIsInstance(list(match.groupdict().values())[0], bytes)


    # Synchronized with test_end
    def test_start(self):
        # returns index of the start of the substring matched by group
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.start(3), 2)

        # returns index of the start of the whole matching substring if given no argument
        pattern = re.compile("(a)(b)(c)")
        match = pattern.search("xyz abc")
        self.assertEqual(match.start(), 4)

        # returns -1 if group exists but did not contribute to the match
        match = re.match("(a)(b)(c)?", "ab")
        self.assertEqual(match.start(3), -1)

        # accepts a group name
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match.start("G1"), 0)

        # raises IndexError exception when given negative group number
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.start(-1)

        # raises IndexError exception when given group number larger than the number of groups defined in the pattern
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.start(10)

        # raises IndexError exception if a string argument is not used as a group name in the pattern
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.start("G4")

        # raises IndexError exception when given argument is not integer or string
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.start(range(1, 2))

        # supports bytes-like source
        # bytes
        match = re.match(b"(a)(b)(c)", b"abc")
        self.assertEqual(match.start(3), 2)
        # bytearray
        match = re.match(b"(a)(b)(c)", bytearray(b"abc"))
        self.assertEqual(match.start(3), 2)


    # Synchronized with test_start
    def test_end(self):
        # returns index of the end of the substring matched by group
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.end(1), 1)

        # returns index of the end of the whole matching substring if given no argument
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.end(), 3)

        # returns -1 if group exists but did not contribute to the match
        match = re.match("(a)(b)(c)?", "ab")
        self.assertEqual(match.end(3), -1)

        # accepts a group name
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match.end("G1"), 1)

        # raises IndexError exception when given negative group number
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.end(-1)

        # raises IndexError exception when given group number larger than the number of groups defined in the pattern
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.end(10)

        # raises IndexError exception if a string argument is not used as a group name in the pattern
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.end("G4")

        # raises IndexError exception when given argument is not integer or string
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.end(range(1, 2))

        # supports bytes-like source
        # bytes
        match = re.match(b"(a)(b)(c)", b"abc")
        self.assertEqual(match.end(1), 1)
        # bytearray
        match = re.match(b"(a)(b)(c)", bytearray(b"abc"))
        self.assertEqual(match.end(1), 1)


    def test_span(self):
        # returns the 2-tuple (m.start(group), m.end(group)) for a match m
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.span(3), (2, 3))

        # returns indices of the whole matching substring if given no argument
        pattern = re.compile("(a)(b)(c)")
        match = pattern.search("xyz abc")
        self.assertEqual(match.span(), (4, 7))

        # returns (-1, -1) if group exists but did not contribute to the match
        match = re.match("(a)(b)(c)?", "ab")
        self.assertEqual(match.span(3), (-1, -1))

        # accepts a group name
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match.span("G1"), (0, 1))

        # raises IndexError exception when given negative group number
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.span(-1)

        # raises IndexError exception when given group number larger than the number of groups defined in the pattern
        match = re.match("(a)(b)(c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.span(10)

        # raises IndexError exception if a string argument is not used as a group name in the pattern
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.span("G4")

        # raises IndexError exception when given argument is not integer or string
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        with self.assertRaisesRegex(IndexError, "no such group"):
            match.span(range(1, 2))

        # supports bytes-like source
        # bytes
        match = re.match(b"(a)(b)(c)", b"abc")
        self.assertEqual(match.span(3), (2, 3))
        # bytearray
        match = re.match(b"(a)(b)(c)", bytearray(b"abc"))
        self.assertEqual(match.span(3), (2, 3))


    def test_pos(self):
        # returns an index in the string at which the RE engine started looking for a match
        pattern = re.compile("(a)(b)(c)")
        match = pattern.search("xyz abc", 3)
        self.assertEqual(match.pos, 3)

        # returns 0 if an index wasn't specified explicitly
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.pos, 0)

        # supports bytes-like source
        # bytes
        pattern = re.compile(b"(a)(b)(c)")
        match = pattern.search(b"xyz abc", 3)
        self.assertEqual(match.pos, 3)
        # bytearray
        pattern = re.compile(b"(a)(b)(c)")
        match = pattern.search(bytearray(b"xyz abc"), 3)
        self.assertEqual(match.pos, 3)


    def test_endpos(self):
        # returns an index in the string beyond which the RE engine will not go
        pattern = re.compile("(a)(b)(c)")
        match = pattern.search("xyz abc xyz", 0, 8)
        self.assertEqual(match.endpos, 8)

        # returns 0 if an index wasn't specified explicitly
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.endpos, 3)

        # supports bytes-like source
        # bytes
        pattern = re.compile(b"(a)(b)(c)")
        match = pattern.search(b"xyz abc xyz", 0, 8)
        self.assertEqual(match.endpos, 8)
        # bytearray
        pattern = re.compile(b"(a)(b)(c)")
        match = pattern.search(bytearray(b"xyz abc xyz"), 0, 8)
        self.assertEqual(match.endpos, 8)


    def test_lastindex(self):
        # returns number of the last matched capturing group
        # last defined group is matching
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.lastindex, 3)
        # last defined group is not matching
        match = re.match("(a)(b)(c)?", "ab")
        self.assertEqual(match.lastindex, 2)

        # returns None if no group was matched at all
        match = re.match("abc", "abc")
        self.assertIsNone(match.lastindex)

        # supports bytes-like source
        # bytes
        match = re.match(b"(a)(b)(c)", b"abc")
        self.assertEqual(match.lastindex, 3)
        # bytearray
        match = re.match(b"(a)(b)(c)", bytearray(b"abc"))
        self.assertEqual(match.lastindex, 3)


    def test_lastgroup(self):
        # returns name of the last matched capturing group
        # last defined group is matching
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)", "abc")
        self.assertEqual(match.lastgroup, "G3")
        # last defined group is not matching
        match = re.match("(?P<G1>a)(?P<G2>b)(?P<G3>c)?", "ab")
        self.assertEqual(match.lastindex, 2)

        # returns None if the group didn't have a name
        match = re.match("(a)(b)(c)", "abc")
        self.assertIsNone(match.lastgroup)

        # returns None if the group didn't have a name but there are named matched groups
        match = re.match("(a)(?P<B>b)(c)", "abc")
        self.assertIsNone(match.lastgroup)

        # returns None if no group was matched at all
        match = re.match("abc", "abc")
        self.assertIsNone(match.lastgroup)

        # supports bytes-like source
        # bytes
        match = re.match(b"(?P<G1>a)(?P<G2>b)(?P<G3>c)", b"abc")
        self.assertIsInstance(match.lastgroup, str)
        self.assertEqual(match.lastgroup, "G3")
        # bytearray
        match = re.match(b"(?P<G1>a)(?P<G2>b)(?P<G3>c)", bytearray(b"abc"))
        self.assertIsInstance(match.lastgroup, str)
        self.assertEqual(match.lastgroup, "G3")


    def test_re(self):
        # returns a regular expression object whose match() or search() method produced this match instance
        pattern = re.compile("(a)(b)(c)")
        match = pattern.search("xyz abc xyz")
        self.assertIs(match.re, pattern)

        # returns a regular expression object implicitly created if no object was used explicitly
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.re, re.compile('(a)(b)(c)'))


    def test_string(self):
        # returns a string object used for matching
        string = "abc"
        match = re.match("(a)(b)(c)", string)
        self.assertIs(match.string, string)


    def test_regs(self):
        # returns a tuple of start and end index pairs for every group, starting from 0
        match = re.match("(a)(b)(c)", "abc")
        self.assertEqual(match.regs, ((0, 3), (0, 1), (1, 2), (2, 3)))

        # returns (-1, -1) for a not matching group
        match = re.match("(a)(b)(c)?", "ab")
        self.assertEqual(match.regs, ((0, 2), (0, 1), (1, 2), (-1, -1)))


    def test_repr(self):
        match = re.match("(abc)", "abc xyz")
        self.assertEqual(repr(match), "<re.Match object; span=(0, 3), match='abc'>")

        # supports bytes-like source
        # bytes
        match = re.match(b"(abc)", b"abc xyz")
        self.assertEqual(repr(match), "<re.Match object; span=(0, 3), match=b'abc'>")
        # bytearray
        match = re.match(b"(abc)", bytearray(b"abc xyz"))
        self.assertEqual(repr(match), "<re.Match object; span=(0, 3), match=b'abc'>")


    def test_str(self):
        match = re.match("(abc)", "abc xyz")
        self.assertEqual(str(match), "<re.Match object; span=(0, 3), match='abc'>")


    def test_subclassing(self):
        with self.assertRaisesRegex(TypeError, "type 're.Match' is not an acceptable base type"):
            class A(re.Match):
                pass


class SREScannerTest(unittest.TestCase):
    # _sre.SRE_Scanner is not publicly visible so test it through Pattern#scanner()

    def test_pattern(self):
        # returns the corresponding Pattern object
        pattern = re.compile('abc')
        scanner = pattern.scanner(string = 'abc')
        self.assertEqual(scanner.pattern, pattern)


    # should be synced with tests for Pattern#match()
    def test_match(self):
       # returns a corresponding Match if zero or more characters at the beginning of string match this regular expression
       pattern = re.compile("d")
       scanner = pattern.scanner("dog")
       match = scanner.match()

       self.assertIsInstance(match, re.Match)
       self.assertEqual(match.group(0), "d")
       self.assertEqual(match.start(), 0)
       self.assertEqual(match.end(), 1)
       self.assertEqual(match.string, "dog")
       self.assertEqual(match.re, pattern)


       # advances starting position after successful repeated matching
       pattern = re.compile("..")
       scanner = pattern.scanner("abcdef")
       match = scanner.match()
       self.assertEqual(match.group(0), "ab")
       match = scanner.match()
       self.assertEqual(match.group(0), "cd")
       match = scanner.match()
       self.assertEqual(match.group(0), "ef")
       match = scanner.match()
       self.assertIsNone(match)


       # does not advance starting position after failed matching
       pattern = re.compile(r"\d")
       scanner = pattern.scanner("a123")
       match = scanner.match()
       self.assertIsNone(match)
       match = scanner.match()
       self.assertIsNone(match)


       # returns None if the string does not match the pattern
       pattern = re.compile("A")
       scanner = pattern.scanner("dog")
       match = scanner.match()
       self.assertIsNone(match)


       # returns None if the string does match the pattern but not at the beginning of string
       pattern = re.compile("g")
       scanner = pattern.scanner("dog")
       match = scanner.match()
       self.assertIsNone(match)


       # the optional second parameter pos gives an index in the string where the search is to start
       pattern = re.compile("o")

       scanner = pattern.scanner("dog", 1)
       match = scanner.match()
       self.assertIsInstance(match, re.Match)

       scanner = pattern.scanner("dog", 2)
       match = scanner.match()
       self.assertIsNone(match)


       # the optional third parameter limits how far the string will be searched
       pattern = re.compile("og")

       scanner = pattern.scanner("dog", 1, 3)
       match = scanner.match()
       self.assertIsInstance(match, re.Match)

       scanner = pattern.scanner("dog", 1, 2)
       match = scanner.match()
       self.assertIsNone(match)


       # returns None if pos >= endpos
       pattern = re.compile("o")

       scanner = pattern.scanner("dog", pos = 1, endpos = 0)
       match = scanner.match()
       self.assertIsNone(match)

       # accepts pos < 0 and treats it as 0
       pattern = re.compile("dog")
       scanner = pattern.scanner("dog 123", -10)
       match = scanner.match()
       self.assertIsInstance(match, re.Match)
       self.assertEqual(match.group(0), "dog")

       # accepts endpos > length and treats it as length
       pattern = re.compile("dog")
       scanner = pattern.scanner("dog 123", 0, 100)
       match = scanner.match()
       self.assertIsInstance(match, re.Match)
       self.assertEqual(match.group(0), "dog")


       # accepts pos > length and returns None
       pattern = re.compile("dog")
       scanner = pattern.scanner("dog 123", 100)
       match = scanner.match()
       self.assertIsNone(match)


       # accepts a string as a "string" keyword argument
       pattern = re.compile("d")
       scanner = pattern.scanner(string = "dog")
       match = scanner.match()
       self.assertIsInstance(match, re.Match)


       # accepts start position as a "pos" keyword argument
       pattern = re.compile("o")
       scanner = pattern.scanner("dog", pos = 1)
       match = scanner.match()
       self.assertIsInstance(match, re.Match)


       # accepts end position as an "endpos" keyword argument
       pattern = re.compile("og")
       scanner = pattern.scanner("dog", 1, endpos = 3)
       match = scanner.match()
       self.assertIsInstance(match, re.Match)


       # converts pos into an integer calling __index__ on it
       pattern = re.compile("o")
       scanner = pattern.scanner("dog", IntegerLike(1))
       match = scanner.match()
       self.assertIsInstance(match, re.Match)


       # converts endpos into an integer calling __index__ on it
       pattern = re.compile("o")
       scanner = pattern.scanner("dog", 1, IntegerLike(3))
       match = scanner.match()
       self.assertIsInstance(match, re.Match)


       # supports bytes-like source

       # bytes
       pattern = re.compile(b"dog")
       scanner = pattern.scanner(b"dog")
       match = scanner.match()
       self.assertIsInstance(match, re.Match)
       self.assertEqual(match.group(0), b"dog")

       # bytearray
       pattern = re.compile(b"dog")
       scanner = pattern.scanner(bytearray(b"dog"))
       match = scanner.match()
       self.assertIsInstance(match, re.Match)
       self.assertEqual(match.group(0), b"dog")


    def test_search(self):
        # scans through string looking for the first location where this regular expression produces a match
        pattern = re.compile("o")
        scanner = pattern.scanner("dog")
        match = scanner.search()

        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), "o")
        self.assertEqual(match.start(), 1)
        self.assertEqual(match.end(), 2)
        self.assertEqual(match.string, "dog")
        self.assertEqual(match.re, pattern)


        # advances starting position after successful repeated searching
        pattern = re.compile(r"\w\w")
        scanner = pattern.scanner("...abcdef")
        match = scanner.search()
        self.assertEqual(match.group(0), "ab")
        match = scanner.search()
        self.assertEqual(match.group(0), "cd")
        match = scanner.search()
        self.assertEqual(match.group(0), "ef")
        match = scanner.search()
        self.assertIsNone(match)


        # returns None if no position in the string matches the pattern
        pattern = re.compile("Z")
        scanner = pattern.scanner("dog")
        match = scanner.search()
        self.assertIsNone(match)


        # the optional second parameter pos gives an index in the string where the search is to start
        pattern = re.compile("o")

        scanner = pattern.scanner("dog", 1)
        match = scanner.search()
        self.assertEqual(match.group(0), "o")

        scanner = pattern.scanner("dog", 2)
        match = scanner.search()
        self.assertIsNone(match)


        # the optional parameter endpos limits how far the string will be searched
        pattern = re.compile("o")

        scanner = pattern.scanner("dog", 0, 2)
        match = scanner.search()
        self.assertEqual(match.group(0), "o")

        scanner = pattern.scanner("dog", 0, 1)
        match = scanner.search()
        self.assertIsNone(match)


        # returns None if pos >= endpos
        pattern = re.compile("o")

        scanner = pattern.scanner("dog", pos = 3, endpos = 0)
        match = scanner.search()
        self.assertIsNone(match)


        # accepts pos < 0 and treats it as 0
        pattern = re.compile("dog")
        scanner = pattern.scanner("dog 123", -10)
        match = scanner.search()
        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), "dog")

        # accepts endpos > length and treats it as length
        pattern = re.compile("dog")
        scanner = pattern.scanner("dog 123", 0, 100)
        match = scanner.search()
        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), "dog")


        # accepts pos > length and returns None
        pattern = re.compile("dog")
        scanner = pattern.scanner("dog 123", 100)
        match = scanner.search()
        self.assertIsNone(match)


        # accepts a string as a "string" keyword argument
        pattern = re.compile("o")
        scanner = pattern.scanner(string = "dog")
        match = scanner.search()
        self.assertIsInstance(match, re.Match)


        # accepts start position as a "pos" keyword argument
        pattern = re.compile("o")
        scanner = pattern.scanner("dog", pos = 1)
        match = scanner.search()
        self.assertIsInstance(match, re.Match)


        # accepts end position as an "endpos" keyword argument
        pattern = re.compile("o")
        scanner = pattern.scanner("dog", endpos = 2)
        match = scanner.search()
        self.assertIsInstance(match, re.Match)


        # converts pos into an integer calling __index__ on it
        pattern = re.compile("o")
        scanner = pattern.scanner("dog", IntegerLike(1))
        match = scanner.search()
        self.assertEqual(match.group(0), "o")


        # converts endpos into an integer calling __index__ on it
        pattern = re.compile("o")
        scanner = pattern.scanner("dog", 0, IntegerLike(2))
        match = scanner.search()
        self.assertEqual(match.group(0), "o")


        # supports bytes-like source

        # bytes
        pattern = re.compile(b"dog")
        scanner = pattern.scanner(b"dog")
        match = scanner.search()
        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), b"dog")

        # bytearray
        pattern = re.compile(b"dog")
        scanner = pattern.scanner(bytearray(b"dog"))
        match = scanner.search()
        self.assertIsInstance(match, re.Match)
        self.assertEqual(match.group(0), b"dog")
