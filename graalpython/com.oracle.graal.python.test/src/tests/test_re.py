# Copyright (c) 2019, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import re
import string
import unittest
import sys


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
        # TODO enable once unicode is supported
#         re.compile('(?P<𝔘𝔫𝔦𝔠𝔬𝔡𝔢>x)(?P=𝔘𝔫𝔦𝔠𝔬𝔡𝔢)(?(𝔘𝔫𝔦𝔠𝔬𝔡𝔢)y)')
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

def test_none_value():
    path_tokenizer = re.compile(
        r"(//?| ==?)|([[]]+)"
    ).findall

    stream = iter([ (special,text)
                    for (special,text) in path_tokenizer('[]')
                    if special or text ])

    n = next(stream)
    assert not n[0]
    assert str(n[0]) == 'None'

