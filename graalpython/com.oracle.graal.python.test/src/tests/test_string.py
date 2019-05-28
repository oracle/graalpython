# Copyright (c) 2018, 2019, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import unittest
import string
import sys


class MyIndexable(object):
    def __init__(self, value):
        self.value = value
    def __index__(self):
        return self.value


def test_find():
    assert "teststring".find("test") == 0
    assert "teststring".find("string") == 4
    assert "teststring".find("tst") == 3
    assert "teststring".find("st") == 2

    assert "teststring".find("test", 1) == -1
    assert "teststring".find("string", 1) == 4
    assert "teststring".find("tst", 4) == -1
    assert "teststring".find("st", 5) == -1

    assert "teststring".find("test", None, 2) == -1
    assert "teststring".find("string", None, 6) == -1
    assert "teststring".find("tst", None, 2) == -1
    assert "teststring".find("st", None, 4) == 2

    s = 'ahoj cau nazadar ahoj'
    assert s.find('ahoj') == 0
    assert s.find('ahoj', 4) == 17
    assert s.find('ahoj', -3) == -1
    assert s.find('ahoj', -21) == 0
    assert s.find('cau', -21) == 5
    assert s.find('cau', -36, -10) == 5
    assert s.find('cau', None) == 5
    assert s.find('ahoj', None) == 0
    assert s.find('cau', None, 8) == 5
    assert s.find('cau', None, 7) == -1
    assert s.find('u', 3) == 7
    assert s.find('u', 3, 7) == -1
    assert s.find('u', 3, 8) == 7
    assert s.find('u', -18, -13) == 7
    assert s.find('u', -18, -12) == 7
    assert s.find('u', -18, -14) == -1
    assert s.find('u', -14, -13) == 7
    assert s.find('u', -12, -13) == -1
    assert s.find('cau', MyIndexable(4)) == 5
    assert s.find('cau', MyIndexable(5)) == 5
    assert s.find('cau', MyIndexable(5), None) == 5
    assert s.find('cau', MyIndexable(5), MyIndexable(8)) == 5
    assert s.find('cau', None, MyIndexable(8)) == 5


def test_rfind():
    assert "test string test".rfind("test") == 12
    assert "test string test".rfind("string") == 5
    assert "test string".rfind("test", 5) == -1
    assert "test string test".rfind("test", None, 12) == 0
    assert "test string test".rfind("test", 4) == 12
    assert "test string test".rfind("test", 4, 12) == -1
    assert "test string test".rfind("test", 4, 14) == -1
    assert "test string test".rfind("test", None, 14) == 0

    s = 'ahoj cau nazdar ahoj'
    assert s.rfind('cau', None, None) == 5
    assert s.rfind('cau', -25, None) == 5
    assert s.rfind('cau', -25, -3) == 5
    assert s.rfind('cau', -25, -12) == 5
    assert s.rfind('cau', -25, -13) == -1
    assert s.rfind('cau', -15, -12) == 5
    assert s.rfind('cau', -14, -12) == -1
    assert s.rfind('ahoj', -14) == 16
    assert s.rfind('ahoj', -4) == 16
    assert s.rfind('ahoj', -3) == -1
    assert s.rfind('ahoj', 16) == 16
    assert s.rfind('ahoj', 16, 20) == 16
    assert s.rfind('ahoj', 16, 19) == -1


def test_format():
    assert "{}.{}".format("part1", "part2") == "part1.part2"
    assert "{0}.{1}".format("part1", "part2") == "part1.part2"
    assert "{1}.{0}".format("part1", "part2") == "part2.part1"
    assert "{}".format("part1") == "part1"


class FormattingTestClass:
    def __repr__(self):
        return "FormattingTestClass.repr"
    def __str__(self):
        return "FormattingTestClass.str"


def test_format_conversion():
    obj = FormattingTestClass()
    assert "{!r}".format(obj) == "FormattingTestClass.repr", "format conversion 'r' failed"
    assert "{!s}".format(obj) == "FormattingTestClass.str", "format conversion 's' failed"
    # TODO function 'ascii' not available
    #assert "{!a}".format(obj) == "FormattingTestClass.repr", "format conversion 'a' failed"
    try:
        "{!:s}".format("2")
        assert False, "expected error for missing conversion specifier"
    except ValueError as e:
        #assert str(e) == "expected ':' after conversion specifier", "invalid error message"
        assert True
    except:
        assert False, "invalid error for missing conversion specifier"

    try:
        "{!x}".format(2)
        assert False, "expected error for wrong conversion specifier"
    except ValueError as e:
        #assert str(e) == "Unknown conversion specifier x", "invalid error message"
        assert True
    except:
        assert False, "invalid error for wrong conversion specifier"


def test_join0():
    assert ', '.join(str(i) for i in range(10)) == "0, 1, 2, 3, 4, 5, 6, 7, 8, 9"
    assert ', '.join(str(i) for i in range(0)) == ""
    assert ', '.join(str(i) for i in range(3)) == "0, 1, 2"

    try:
        class X:
            pass
        obj = X()
        "x".join([obj])
        assert False
    except Exception as e:
        assert type(e) is TypeError, str(e)

    assert ', '.join(["hello", "world"]) == "hello, world"


def test_join1():
    class CustomList(list):
        def __iter__(self):
            return iter(["1", "2", "3"])
    assert ", ".join(CustomList(["A", "B", "C"])) == "1, 2, 3"


def test_strip():
    assert ' test  '.strip() == 'test'
    assert u' test  '.strip() == u'test'


def assertEqual(value, expected):
    assert value == expected, ("'%s' was expected to be equal to '%s'" % (value, expected))


def assertRaises(error, func, *args, **kwargs):
    try:
        func(*args, **kwargs)
    except BaseException as e:
        if isinstance(e, error):
            return
        else:
            assert False, "expected %s(%s) to raise %s, but raised %s" % (func, args, error, type(e))
    else:
        assert False, "expected %s(%s) to raise %s, but did not raise" % (func, args, error)


def test_cpython_format():
    assertEqual(''.format(), '')
    assertEqual('a'.format(), 'a')
    assertEqual('ab'.format(), 'ab')
    assertEqual('a{{'.format(), 'a{')
    assertEqual('a}}'.format(), 'a}')
    assertEqual('{{b'.format(), '{b')
    assertEqual('}}b'.format(), '}b')
    assertEqual('a{{b'.format(), 'a{b')

    # examples from the PEP:
    assertEqual("My name is {0}".format('Fred'), "My name is Fred")
    assertEqual("My name is {0[name]}".format(dict(name='Fred')),
                     "My name is Fred")
    assertEqual("My name is {0} :-{{}}".format('Fred'),
                     "My name is Fred :-{}")

    # classes we'll use for testing
    class C:
        def __init__(self, x=100):
            self._x = x
        def __format__(self, spec):
            return spec

    class D:
        def __init__(self, x):
            self.x = x
        def __format__(self, spec):
            return str(self.x)

    # class with __str__, but no __format__
    class E:
        def __init__(self, x):
            self.x = x
        def __str__(self):
            return 'E(' + self.x + ')'

    # class with __repr__, but no __format__ or __str__
    class F:
        def __init__(self, x):
            self.x = x
        def __repr__(self):
            return 'F(' + self.x + ')'

    # class with __format__ that forwards to string, for some format_spec's
    class G:
        def __init__(self, x):
            self.x = x
        def __str__(self):
            return "string is " + self.x
        def __format__(self, format_spec):
            if format_spec == 'd':
                return 'G(' + self.x + ')'
            return object.__format__(self, format_spec)

    class J(int):
        def __format__(self, format_spec):
            return int.__format__(self * 2, format_spec)

    class M:
        def __init__(self, x):
            self.x = x
        def __repr__(self):
            return 'M(' + self.x + ')'
        __str__ = None

    class N:
        def __init__(self, x):
            self.x = x
        def __repr__(self):
            return 'N(' + self.x + ')'
        __format__ = None

    assertEqual(''.format(), '')
    assertEqual('abc'.format(), 'abc')
    assertEqual('{0}'.format('abc'), 'abc')
    assertEqual('{0:}'.format('abc'), 'abc')
    assertEqual('X{0}'.format('abc'), 'Xabc')
    assertEqual('{0}X'.format('abc'), 'abcX')
    assertEqual('X{0}Y'.format('abc'), 'XabcY')
    assertEqual('{1}'.format(1, 'abc'), 'abc')
    assertEqual('X{1}'.format(1, 'abc'), 'Xabc')
    assertEqual('{1}X'.format(1, 'abc'), 'abcX')
    assertEqual('X{1}Y'.format(1, 'abc'), 'XabcY')
    assertEqual('{0}'.format(-15), '-15')
    assertEqual('{0}{1}'.format(-15, 'abc'), '-15abc')
    assertEqual('{0}X{1}'.format(-15, 'abc'), '-15Xabc')
    assertEqual('{{'.format(), '{')
    assertEqual('}}'.format(), '}')
    assertEqual('{{}}'.format(), '{}')
    assertEqual('{{x}}'.format(), '{x}')
    assertEqual('{{{0}}}'.format(123), '{123}')
    assertEqual('{{{{0}}}}'.format(), '{{0}}')
    assertEqual('}}{{'.format(), '}{')
    assertEqual('}}x{{'.format(), '}x{')

    # weird field names
    assertEqual("{0[foo-bar]}".format({'foo-bar':'baz'}), 'baz')
    assertEqual("{0[foo bar]}".format({'foo bar':'baz'}), 'baz')
    assertEqual("{0[ ]}".format({' ':3}), '3')

    assertEqual('{foo._x}'.format(foo=C(20)), '20')
    assertEqual('{1}{0}'.format(D(10), D(20)), '2010')
    assertEqual('{0._x.x}'.format(C(D('abc'))), 'abc')
    assertEqual('{0[0]}'.format(['abc', 'def']), 'abc')
    assertEqual('{0[1]}'.format(['abc', 'def']), 'def')
    assertEqual('{0[1][0]}'.format(['abc', ['def']]), 'def')
    assertEqual('{0[1][0].x}'.format(['abc', [D('def')]]), 'def')

    # strings
    # assertEqual('{0:.3s}'.format('abc'), 'abc')
    # assertEqual('{0:.3s}'.format('ab'), 'ab')
    # assertEqual('{0:.3s}'.format('abcdef'), 'abc')
    # assertEqual('{0:.0s}'.format('abcdef'), '')
    # assertEqual('{0:3.3s}'.format('abc'), 'abc')
    # assertEqual('{0:2.3s}'.format('abc'), 'abc')
    # assertEqual('{0:2.2s}'.format('abc'), 'ab')
    # assertEqual('{0:3.2s}'.format('abc'), 'ab ')
    # assertEqual('{0:x<0s}'.format('result'), 'result')
    # assertEqual('{0:x<5s}'.format('result'), 'result')
    # assertEqual('{0:x<6s}'.format('result'), 'result')
    # assertEqual('{0:x<7s}'.format('result'), 'resultx')
    # assertEqual('{0:x<8s}'.format('result'), 'resultxx')
    # assertEqual('{0: <7s}'.format('result'), 'result ')
    # assertEqual('{0:<7s}'.format('result'), 'result ')
    # assertEqual('{0:>7s}'.format('result'), ' result')
    # assertEqual('{0:>8s}'.format('result'), '  result')
    # assertEqual('{0:^8s}'.format('result'), ' result ')
    # assertEqual('{0:^9s}'.format('result'), ' result  ')
    # assertEqual('{0:^10s}'.format('result'), '  result  ')
    # assertEqual('{0:10000}'.format('a'), 'a' + ' ' * 9999)
    # assertEqual('{0:10000}'.format(''), ' ' * 10000)
    # assertEqual('{0:10000000}'.format(''), ' ' * 10000000)

    # issue 12546: use \x00 as a fill character
    # assertEqual('{0:\x00<6s}'.format('foo'), 'foo\x00\x00\x00')
    # assertEqual('{0:\x01<6s}'.format('foo'), 'foo\x01\x01\x01')
    # assertEqual('{0:\x00^6s}'.format('foo'), '\x00foo\x00\x00')
    # assertEqual('{0:^6s}'.format('foo'), ' foo  ')

    # assertEqual('{0:\x00<6}'.format(3), '3\x00\x00\x00\x00\x00')
    # assertEqual('{0:\x01<6}'.format(3), '3\x01\x01\x01\x01\x01')
    # assertEqual('{0:\x00^6}'.format(3), '\x00\x003\x00\x00\x00')
    # assertEqual('{0:<6}'.format(3), '3     ')

    # assertEqual('{0:\x00<6}'.format(3.14), '3.14\x00\x00')
    # assertEqual('{0:\x01<6}'.format(3.14), '3.14\x01\x01')
    # assertEqual('{0:\x00^6}'.format(3.14), '\x003.14\x00')
    # assertEqual('{0:^6}'.format(3.14), ' 3.14 ')

    # assertEqual('{0:\x00<12}'.format(3+2.0j), '(3+2j)\x00\x00\x00\x00\x00\x00')
    # assertEqual('{0:\x01<12}'.format(3+2.0j), '(3+2j)\x01\x01\x01\x01\x01\x01')
    # assertEqual('{0:\x00^12}'.format(3+2.0j), '\x00\x00\x00(3+2j)\x00\x00\x00')
    # assertEqual('{0:^12}'.format(3+2.0j), '   (3+2j)   ')

    # format specifiers for user defined type
    assertEqual('{0:abc}'.format(C()), 'abc')

    # !r, !s and !a coercions
    assertEqual('{0!s}'.format('Hello'), 'Hello')
    assertEqual('{0!s:}'.format('Hello'), 'Hello')
    # assertEqual('{0!s:15}'.format('Hello'), 'Hello          ')
    # assertEqual('{0!s:15s}'.format('Hello'), 'Hello          ')
    assertEqual('{0!r}'.format('Hello'), "'Hello'")
    assertEqual('{0!r:}'.format('Hello'), "'Hello'")
    assertEqual('{0!r}'.format(F('Hello')), 'F(Hello)')
    # assertEqual('{0!r}'.format('\u0378'), "'\\u0378'") # nonprintable
    # assertEqual('{0!r}'.format('\u0374'), "'\u0374'")  # printable
    assertEqual('{0!r}'.format(F('\u0374')), 'F(\u0374)')
    # assertEqual('{0!a}'.format('Hello'), "'Hello'")
    # assertEqual('{0!a}'.format('\u0378'), "'\\u0378'") # nonprintable
    # assertEqual('{0!a}'.format('\u0374'), "'\\u0374'") # printable
    # assertEqual('{0!a:}'.format('Hello'), "'Hello'")
    # assertEqual('{0!a}'.format(F('Hello')), 'F(Hello)')
    # assertEqual('{0!a}'.format(F('\u0374')), 'F(\\u0374)')

    # test fallback to object.__format__
    assertEqual('{0}'.format({}), '{}')
    assertEqual('{0}'.format([]), '[]')
    assertEqual('{0}'.format([1]), '[1]')

    assertEqual('{0:d}'.format(G('data')), 'G(data)')
    assertEqual('{0!s}'.format(G('data')), 'string is data')

    # assertRaises(TypeError, '{0:^10}'.format, E('data'))
    # assertRaises(TypeError, '{0:^10s}'.format, E('data'))
    # assertRaises(TypeError, '{0:>15s}'.format, G('data'))

    # test deriving from a builtin type and overriding __format__
    assertEqual("{0}".format(J(10)), "20")


    # string format specifiers
    assertEqual('{0:}'.format('a'), 'a')

    # computed format specifiers
    # assertEqual("{0:.{1}}".format('hello world', 5), 'hello')
    # assertEqual("{0:.{1}s}".format('hello world', 5), 'hello')
    # assertEqual("{0:.{precision}s}".format('hello world', precision=5), 'hello')
    # assertEqual("{0:{width}.{precision}s}".format('hello world', width=10, precision=5), 'hello     ')
    # assertEqual("{0:{width}.{precision}s}".format('hello world', width='10', precision='5'), 'hello     ')

    # test various errors
    assertRaises(ValueError, '{'.format)
    assertRaises(ValueError, '}'.format)
    assertRaises(ValueError, 'a{'.format)
    assertRaises(ValueError, 'a}'.format)
    assertRaises(ValueError, '{a'.format)
    assertRaises(ValueError, '}a'.format)
    assertRaises(IndexError, '{0}'.format)
    assertRaises(IndexError, '{1}'.format, 'abc')
    assertRaises(KeyError,   '{x}'.format)
    assertRaises(ValueError, "}{".format)
    assertRaises(ValueError, "abc{0:{}".format)
    assertRaises(ValueError, "{0".format)
    assertRaises(IndexError, "{0.}".format)
    assertRaises(ValueError, "{0.}".format, 0)
    assertRaises(ValueError, "{0[}".format)
    assertRaises(ValueError, "{0[}".format, [])
    assertRaises(KeyError,   "{0]}".format)
    assertRaises(ValueError, "{0.[]}".format, 0)
    assertRaises(ValueError, "{0..foo}".format, 0)
    assertRaises(ValueError, "{0[0}".format, 0)
    assertRaises(ValueError, "{0[0:foo}".format, 0)
    assertRaises(KeyError,   "{c]}".format)
    assertRaises(ValueError, "{{ {{{0}}".format, 0)
    assertRaises(ValueError, "{0}}".format, 0)
    assertRaises(KeyError,   "{foo}".format, bar=3)
    assertRaises(ValueError, "{0!x}".format, 3)
    assertRaises(ValueError, "{0!}".format, 0)
    assertRaises(ValueError, "{0!rs}".format, 0)
    assertRaises(ValueError, "{!}".format)
    assertRaises(IndexError, "{:}".format)
    assertRaises(IndexError, "{:s}".format)
    assertRaises(IndexError, "{}".format)
    big = "23098475029384702983476098230754973209482573"
    # assertRaises(ValueError, ("{" + big + "}").format)
    # assertRaises(ValueError, ("{[" + big + "]}").format, [0])

    # issue 6089
    assertRaises(ValueError, "{0[0]x}".format, [None])
    assertRaises(ValueError, "{0[0](10)}".format, [None])

    # can't have a replacement on the field name portion
    # assertRaises(TypeError, '{0[{1}]}'.format, 'abcdefg', 4)

    # exceed maximum recursion depth
    assertRaises(ValueError, "{0:{1:{2}}}".format, 'abc', 's', '')
    assertRaises(ValueError, "{0:{1:{2:{3:{4:{5:{6}}}}}}}".format,
                      0, 1, 2, 3, 4, 5, 6, 7)

    # string format spec errors
    # assertRaises(ValueError, "{0:-s}".format, '')
    # assertRaises(ValueError, format, "", "-")
    # assertRaises(ValueError, "{0:=s}".format, '')

    # Alternate formatting is not supported
    # assertRaises(ValueError, format, '', '#')
    # assertRaises(ValueError, format, '', '#20')

    # Non-ASCII
    assertEqual("{0:s}{1:s}".format("ABC", "\u0410\u0411\u0412"),
                     'ABC\u0410\u0411\u0412')
    # assertEqual("{0:.3s}".format("ABC\u0410\u0411\u0412"),
    #                  'ABC')
    # assertEqual("{0:.0s}".format("ABC\u0410\u0411\u0412"),
    #                  '')

    assertEqual("{[{}]}".format({"{}": 5}), "5")
    assertEqual("{[{}]}".format({"{}" : "a"}), "a")
    assertEqual("{[{]}".format({"{" : "a"}), "a")
    assertEqual("{[}]}".format({"}" : "a"}), "a")
    assertEqual("{[[]}".format({"[" : "a"}), "a")
    assertEqual("{[!]}".format({"!" : "a"}), "a")
    assertRaises(ValueError, "{a{}b}".format, 42)
    assertRaises(ValueError, "{a{b}".format, 42)
    assertRaises(ValueError, "{[}".format, 42)

    # assertEqual("0x{:0{:d}X}".format(0x0,16), "0x0000000000000000")

    # Blocking fallback
    m = M('data')
    assertEqual("{!r}".format(m), 'M(data)')
    assertRaises(TypeError, "{!s}".format, m)
    assertRaises(TypeError, "{}".format, m)
    n = N('data')
    assertEqual("{!r}".format(n), 'N(data)')
    assertEqual("{!s}".format(n), 'N(data)')
    assertRaises(TypeError, "{}".format, n)


class UnicodeTest(unittest.TestCase):
    # The type to be tested
    # Change in subclasses to change the behaviour of fixtesttype()
    type2test = str

    def checkequalnofix(self, result, object, methodname, *args):
        method = getattr(object, methodname)
        realresult = method(*args)
        self.assertEqual(realresult, result)
        self.assertTrue(type(realresult) is type(result))

        # if the original is returned make sure that
        # this doesn't happen with subclasses
        if realresult is object:
            class usub(str):
                def __repr__(self):
                    return 'usub(%r)' % str.__repr__(self)
            object = usub(object)
            method = getattr(object, methodname)
            realresult = method(*args)
            self.assertEqual(realresult, result)
            self.assertTrue(object is not realresult)

    # All tests pass their arguments to the testing methods
    # as str objects. fixtesttype() can be used to propagate
    # these arguments to the appropriate type
    def fixtype(self, obj):
        if isinstance(obj, str):
            return self.__class__.type2test(obj)
        elif isinstance(obj, list):
            return [self.fixtype(x) for x in obj]
        elif isinstance(obj, tuple):
            return tuple([self.fixtype(x) for x in obj])
        elif isinstance(obj, dict):
            return dict([
               (self.fixtype(key), self.fixtype(value))
               for (key, value) in obj.items()
            ])
        else:
            return obj

    def test_fixtype(self):
        self.assertIs(type(self.fixtype("123")), self.type2test)

    def checkequal(self, result, obj, methodname, *args, **kwargs):
        result = self.fixtype(result)
        obj = self.fixtype(obj)
        args = self.fixtype(args)
        kwargs = {k: self.fixtype(v) for k,v in kwargs.items()}
        realresult = getattr(obj, methodname)(*args, **kwargs)
        self.assertEqual(
            result,
            realresult
        )
        # if the original is returned make sure that
        # this doesn't happen with subclasses
        if obj is realresult:
            try:
                class subtype(self.__class__.type2test):
                    pass
            except TypeError:
                pass  # Skip this if we can't subclass
            else:
                obj = subtype(obj)
                realresult = getattr(obj, methodname)(*args)
                self.assertTrue(obj is not realresult)
                #self.assertIsNot(obj, realresult)

    # check that obj.method(*args) raises exc
    def checkraises(self, exc, obj, methodname, *args):
        obj = self.fixtype(obj)
        args = self.fixtype(args)
        with self.assertRaises(exc) as cm:
            getattr(obj, methodname)(*args)
        self.assertNotEqual(str(cm.exception), '')

    def test_islower(self):
        self.checkequal(False, '', 'islower')
        self.checkequal(True, 'a', 'islower')
        self.checkequal(False, 'A', 'islower')
        self.checkequal(False, '\n', 'islower')
        self.checkequal(True, 'abc', 'islower')
        self.checkequal(False, 'aBc', 'islower')
        self.checkequal(True, 'abc\n', 'islower')
        self.checkequal(True, 'a_b!c\n', 'islower')
        self.checkequal(False, 'A_b!c\n', 'islower')
        self.checkraises(TypeError, 'abc', 'islower', 42)
        self.checkequalnofix(False, '\u1FFc', 'islower')
        self.assertFalse('\u2167'.islower())
        self.assertTrue('\u2177'.islower())
        # non-BMP, uppercase
        self.assertFalse('\U00010401'.islower())
        self.assertFalse('\U00010427'.islower())
        # # non-BMP, lowercase
        # self.assertTrue('\U00010429'.islower())
        # self.assertTrue('\U0001044E'.islower())
        # # non-BMP, non-cased
        # self.assertFalse('\U0001F40D'.islower())
        # self.assertFalse('\U0001F46F'.islower())

    def test_isupper(self):
        self.checkequal(False, '', 'isupper')
        self.checkequal(False, 'a', 'isupper')
        self.checkequal(True, 'A', 'isupper')
        self.checkequal(False, '\n', 'isupper')
        self.checkequal(True, 'ABC', 'isupper')
        self.checkequal(False, 'AbC', 'isupper')
        self.checkequal(True, 'ABC\n', 'isupper')
        self.checkequal(True, 'A_B!C\n', 'isupper')
        self.checkequal(False, 'a_B!C\n', 'isupper')
        self.checkraises(TypeError, 'abc', 'isupper', 42)
        if not sys.platform.startswith('java'):
            self.checkequalnofix(False, '\u1FFc', 'isupper')
        self.assertTrue('\u2167'.isupper())
        self.assertFalse('\u2177'.isupper())
        # # non-BMP, uppercase
        # self.assertTrue('\U00010401'.isupper())
        # self.assertTrue('\U00010427'.isupper())
        # # non-BMP, lowercase
        # self.assertFalse('\U00010429'.isupper())
        # self.assertFalse('\U0001044E'.isupper())
        # # non-BMP, non-cased
        # self.assertFalse('\U0001F40D'.isupper())
        # self.assertFalse('\U0001F46F'.isupper())

    def test_istitle(self):
        self.checkequal(False, '', 'istitle')
        self.checkequal(False, 'a', 'istitle')
        self.checkequal(True, 'A', 'istitle')
        self.checkequal(False, '\n', 'istitle')
        self.checkequal(True, 'A Titlecased Line', 'istitle')
        self.checkequal(True, 'A\nTitlecased Line', 'istitle')
        self.checkequal(True, 'A Titlecased, Line', 'istitle')
        self.checkequal(False, 'Not a capitalized String', 'istitle')
        self.checkequal(False, 'Not\ta Titlecase String', 'istitle')
        self.checkequal(False, 'Not--a Titlecase String', 'istitle')
        self.checkequal(False, 'NOT', 'istitle')
        self.checkraises(TypeError, 'abc', 'istitle', 42)
        self.checkequalnofix(True, '\u1FFc', 'istitle')
        self.checkequalnofix(True, 'Greek \u1FFcitlecases ...', 'istitle')

        # non-BMP, uppercase + lowercase
        # self.assertTrue('\U00010401\U00010429'.istitle())
        # self.assertTrue('\U00010427\U0001044E'.istitle())
        # apparently there are no titlecased (Lt) non-BMP chars in Unicode 6
        # for ch in ['\U00010429', '\U0001044E', '\U0001F40D', '\U0001F46F']:
        #     self.assertFalse(ch.istitle(), '{!a} is not title'.format(ch))

    def test_isspace(self):
        self.checkequal(False, '', 'isspace')
        self.checkequal(False, 'a', 'isspace')
        self.checkequal(True, ' ', 'isspace')
        self.checkequal(True, '\t', 'isspace')
        self.checkequal(True, '\r', 'isspace')
        self.checkequal(True, '\n', 'isspace')
        self.checkequal(True, ' \t\r\n', 'isspace')
        self.checkequal(False, ' \t\r\na', 'isspace')
        self.checkraises(TypeError, 'abc', 'isspace', 42)
        self.checkequalnofix(True, '\u2000', 'isspace')
        self.checkequalnofix(True, '\u200a', 'isspace')
        self.checkequalnofix(False, '\u2014', 'isspace')
        # apparently there are no non-BMP spaces chars in Unicode 6
        # for ch in ['\U00010401', '\U00010427', '\U00010429', '\U0001044E',
        #            '\U0001F40D', '\U0001F46F']:
        #     self.assertFalse(ch.isspace(), '{!a} is not space.'.format(ch))

    def test_isalnum(self):
        self.checkequal(False, '', 'isalnum')
        self.checkequal(True, 'a', 'isalnum')
        self.checkequal(True, 'A', 'isalnum')
        self.checkequal(False, '\n', 'isalnum')
        self.checkequal(True, '123abc456', 'isalnum')
        self.checkequal(True, 'a1b3c', 'isalnum')
        self.checkequal(False, 'aBc000 ', 'isalnum')
        self.checkequal(False, 'abc\n', 'isalnum')
        self.checkraises(TypeError, 'abc', 'isalnum', 42)
        # for ch in ['\U00010401', '\U00010427', '\U00010429', '\U0001044E',
        #            '\U0001D7F6', '\U00011066', '\U000104A0', '\U0001F107']:
        #     self.assertTrue(ch.isalnum(), '{!a} is alnum.'.format(ch))

    def test_isalpha(self):
        self.checkequal(False, '', 'isalpha')
        self.checkequal(True, 'a', 'isalpha')
        self.checkequal(True, 'A', 'isalpha')
        self.checkequal(False, '\n', 'isalpha')
        self.checkequal(True, 'abc', 'isalpha')
        self.checkequal(False, 'aBc123', 'isalpha')
        self.checkequal(False, 'abc\n', 'isalpha')
        self.checkraises(TypeError, 'abc', 'isalpha', 42)
        self.checkequalnofix(True, '\u1FFc', 'isalpha')
        # # non-BMP, cased
        # self.assertTrue('\U00010401'.isalpha())
        # self.assertTrue('\U00010427'.isalpha())
        # self.assertTrue('\U00010429'.isalpha())
        # self.assertTrue('\U0001044E'.isalpha())
        # non-BMP, non-cased
        self.assertFalse('\U0001F40D'.isalpha())
        self.assertFalse('\U0001F46F'.isalpha())

    def test_isdecimal(self):
        self.checkequalnofix(False, '', 'isdecimal')
        self.checkequalnofix(False, 'a', 'isdecimal')
        self.checkequalnofix(True, '0', 'isdecimal')
        self.checkequalnofix(False, '\u2460', 'isdecimal') # CIRCLED DIGIT ONE
        self.checkequalnofix(False, '\xbc', 'isdecimal') # VULGAR FRACTION ONE QUARTER
        self.checkequalnofix(True, '\u0660', 'isdecimal') # ARABIC-INDIC DIGIT ZERO
        self.checkequalnofix(True, '0123456789', 'isdecimal')
        self.checkequalnofix(False, '0123456789a', 'isdecimal')

        self.checkraises(TypeError, 'abc', 'isdecimal', 42)

        # for ch in ['\U00010401', '\U00010427', '\U00010429', '\U0001044E',
        #            '\U0001F40D', '\U0001F46F', '\U00011065', '\U0001F107']:
        #     self.assertFalse(ch.isdecimal(), '{!a} is not decimal.'.format(ch))
        # for ch in ['\U0001D7F6', '\U00011066', '\U000104A0']:
        #     self.assertTrue(ch.isdecimal(), '{!a} is decimal.'.format(ch))

    def test_isdigit(self):
        self.checkequal(False, '', 'isdigit')
        self.checkequal(False, 'a', 'isdigit')
        self.checkequal(True, '0', 'isdigit')
        self.checkequal(True, '0123456789', 'isdigit')
        self.checkequal(False, '0123456789a', 'isdigit')
        self.checkraises(TypeError, 'abc', 'isdigit', 42)
        # self.checkequalnofix(True, '\u2460', 'isdigit')
        self.checkequalnofix(False, '\xbc', 'isdigit')
        self.checkequalnofix(True, '\u0660', 'isdigit')

        # for ch in ['\U00010401', '\U00010427', '\U00010429', '\U0001044E',
        #            '\U0001F40D', '\U0001F46F', '\U00011065']:
        #     self.assertFalse(ch.isdigit(), '{!a} is not a digit.'.format(ch))
        # for ch in ['\U0001D7F6', '\U00011066', '\U000104A0', '\U0001F107']:
        #     self.assertTrue(ch.isdigit(), '{!a} is a digit.'.format(ch))

    def test_isnumeric(self):
        self.checkequalnofix(False, '', 'isnumeric')
        self.checkequalnofix(False, 'a', 'isnumeric')
        self.checkequalnofix(True, '0', 'isnumeric')
        # self.checkequalnofix(True, '\u2460', 'isnumeric')
        # self.checkequalnofix(True, '\xbc', 'isnumeric')
        self.checkequalnofix(True, '\u0660', 'isnumeric')
        self.checkequalnofix(True, '0123456789', 'isnumeric')
        self.checkequalnofix(False, '0123456789a', 'isnumeric')

        self.assertRaises(TypeError, "abc".isnumeric, 42)

        # for ch in ['\U00010401', '\U00010427', '\U00010429', '\U0001044E',
        #            '\U0001F40D', '\U0001F46F']:
        #     self.assertFalse(ch.isnumeric(), '{!a} is not numeric.'.format(ch))
        # for ch in ['\U00011065', '\U0001D7F6', '\U00011066',
        #            '\U000104A0', '\U0001F107']:
        #     self.assertTrue(ch.isnumeric(), '{!a} is numeric.'.format(ch))

    def test_isidentifier(self):
        self.assertTrue("a".isidentifier())
        self.assertTrue("Z".isidentifier())
        self.assertTrue("_".isidentifier())
        self.assertTrue("b0".isidentifier())
        self.assertTrue("bc".isidentifier())
        self.assertTrue("b_".isidentifier())
        self.assertTrue("µ".isidentifier())
        # self.assertTrue("𝔘𝔫𝔦𝔠𝔬𝔡𝔢".isidentifier())

        self.assertFalse(" ".isidentifier())
        self.assertFalse("[".isidentifier())
        self.assertFalse("©".isidentifier())
        self.assertFalse("0".isidentifier())

    def test_isprintable(self):
        self.assertTrue("".isprintable())
        self.assertTrue(" ".isprintable())
        self.assertTrue("abcdefg".isprintable())
        self.assertFalse("abcdefg\n".isprintable())
        # some defined Unicode character
        self.assertTrue("\u0374".isprintable())
        # undefined character
        # self.assertFalse("\u0378".isprintable())
        # single surrogate character
        # self.assertFalse("\ud800".isprintable())

        self.assertTrue('\U0001F46F'.isprintable())
        # self.assertFalse('\U000E0020'.isprintable())

    def test_zfill(self):
        self.checkequal('123', '123', 'zfill', 2)
        self.checkequal('123', '123', 'zfill', 3)
        self.checkequal('0123', '123', 'zfill', 4)
        self.checkequal('+123', '+123', 'zfill', 3)
        self.checkequal('+123', '+123', 'zfill', 4)
        self.checkequal('+0123', '+123', 'zfill', 5)
        self.checkequal('-123', '-123', 'zfill', 3)
        self.checkequal('-123', '-123', 'zfill', 4)
        self.checkequal('-0123', '-123', 'zfill', 5)
        self.checkequal('000', '', 'zfill', 3)
        self.checkequal('34', '34', 'zfill', 1)
        self.checkequal('0034', '34', 'zfill', 4)

        self.checkraises(TypeError, '123', 'zfill')

    def test_zfill_specialization(self):
        self.checkequal('123', '123', 'zfill', True)
        self.checkequal('0123', '123', 'zfill', MyIndexable(4))

    def test_title(self):
        self.checkequal(' Hello ', ' hello ', 'title')
        self.checkequal('Hello ', 'hello ', 'title')
        self.checkequal('Hello ', 'Hello ', 'title')
        self.checkequal('Format This As Title String', "fOrMaT thIs aS titLe String", 'title')
        self.checkequal('Format,This-As*Title;String', "fOrMaT,thIs-aS*titLe;String", 'title', )
        self.checkequal('Getint', "getInt", 'title')
        self.checkraises(TypeError, 'hello', 'title', 42)

    def test_title_uni(self):
        self.assertEqual('\U0001044F'.title(), '\U00010427')
        self.assertEqual('\U0001044F\U0001044F'.title(),
                         '\U00010427\U0001044F')
        self.assertEqual('\U0001044F\U0001044F \U0001044F\U0001044F'.title(),
                         '\U00010427\U0001044F \U00010427\U0001044F')
        self.assertEqual('\U00010427\U0001044F \U00010427\U0001044F'.title(),
                         '\U00010427\U0001044F \U00010427\U0001044F')
        self.assertEqual('\U0001044F\U00010427 \U0001044F\U00010427'.title(),
                         '\U00010427\U0001044F \U00010427\U0001044F')
        self.assertEqual('X\U00010427x\U0001044F X\U00010427x\U0001044F'.title(),
                         'X\U0001044Fx\U0001044F X\U0001044Fx\U0001044F')
        self.assertEqual('ﬁNNISH'.title(), 'Finnish')
        self.assertEqual('bﬁNNISH'.title(), 'Bﬁnnish')
        self.assertEqual('ﬁﬁNNﬁISH'.title(), 'Fiﬁnnﬁish')
        self.assertEqual('A\u03a3A'.title(), 'A\u03c3a')

    def test_ljust(self):
        self.checkequal('abc       ', 'abc', 'ljust', 10)
        self.checkequal('abc   ', 'abc', 'ljust', 6)
        self.checkequal('abc', 'abc', 'ljust', 3)
        self.checkequal('abc', 'abc', 'ljust', 2)
        self.checkequal('abc*******', 'abc', 'ljust', 10, '*')
        self.checkraises(TypeError, 'abc', 'ljust')

    def test_rjust(self):
        self.checkequal('       abc', 'abc', 'rjust', 10)
        self.checkequal('   abc', 'abc', 'rjust', 6)
        self.checkequal('abc', 'abc', 'rjust', 3)
        self.checkequal('abc', 'abc', 'rjust', 2)
        self.checkequal('*******abc', 'abc', 'rjust', 10, '*')
        self.checkraises(TypeError, 'abc', 'rjust')

    def test_center(self):
        self.checkequal('   abc    ', 'abc', 'center', 10)
        self.checkequal(' abc  ', 'abc', 'center', 6)
        self.checkequal('abc', 'abc', 'center', 3)
        self.checkequal('abc', 'abc', 'center', 2)
        self.checkequal('***abc****', 'abc', 'center', 10, '*')
        self.checkraises(TypeError, 'abc', 'center')

    def test_center_uni(self):
        self.assertEqual('x'.center(2, '\U0010FFFF'),
                         'x\U0010FFFF')
        self.assertEqual('x'.center(3, '\U0010FFFF'),
                         '\U0010FFFFx\U0010FFFF')
        self.assertEqual('x'.center(4, '\U0010FFFF'),
                         '\U0010FFFFx\U0010FFFF\U0010FFFF')

    # Whether the "contained items" of the container are integers in
    # range(0, 256) (i.e. bytes, bytearray) or strings of length 1
    # (str)
    contains_bytes = False

    def test_count(self):
        self.checkequal(3, 'aaa', 'count', 'a')
        self.checkequal(0, 'aaa', 'count', 'b')
        self.checkequal(3, 'aaa', 'count', 'a')
        self.checkequal(0, 'aaa', 'count', 'b')
        self.checkequal(3, 'aaa', 'count', 'a')
        self.checkequal(0, 'aaa', 'count', 'b')
        self.checkequal(0, 'aaa', 'count', 'b')
        self.checkequal(2, 'aaa', 'count', 'a', 1)
        self.checkequal(0, 'aaa', 'count', 'a', 10)
        self.checkequal(1, 'aaa', 'count', 'a', -1)
        self.checkequal(3, 'aaa', 'count', 'a', -10)
        self.checkequal(1, 'aaa', 'count', 'a', 0, 1)
        self.checkequal(3, 'aaa', 'count', 'a', 0, 10)
        self.checkequal(2, 'aaa', 'count', 'a', 0, -1)
        self.checkequal(0, 'aaa', 'count', 'a', 0, -10)
        self.checkequal(3, 'aaa', 'count', '', 1)
        self.checkequal(1, 'aaa', 'count', '', 3)
        self.checkequal(0, 'aaa', 'count', '', 10)
        self.checkequal(2, 'aaa', 'count', '', -1)
        self.checkequal(4, 'aaa', 'count', '', -10)

        self.checkequal(1, '', 'count', '')
        self.checkequal(0, '', 'count', '', 1, 1)
        self.checkequal(0, '', 'count', '', sys.maxsize, 0)

        self.checkequal(0, '', 'count', 'xx')
        self.checkequal(0, '', 'count', 'xx', 1, 1)
        self.checkequal(0, '', 'count', 'xx', sys.maxsize, 0)

        self.checkraises(TypeError, 'hello', 'count')

        if self.contains_bytes:
            self.checkequal(0, 'hello', 'count', 42)
        else:
            self.checkraises(TypeError, 'hello', 'count', 42)

        # For a variety of combinations,
        #    verify that str.count() matches an equivalent function
        #    replacing all occurrences and then differencing the string lengths
        charset = ['', 'a', 'b']
        digits = 7
        base = len(charset)
        teststrings = set()
        for i in range(base ** digits):
            entry = []
            for j in range(digits):
                i, m = divmod(i, base)
                entry.append(charset[m])
            teststrings.add(''.join(entry))
        teststrings = [self.fixtype(ts) for ts in teststrings]
        for i in teststrings:
            n = len(i)
            for j in teststrings:
                r1 = i.count(j)
                if j:
                    r2, rem = divmod(n - len(i.replace(j, self.fixtype(''))),
                                     len(j))
                else:
                    r2, rem = len(i)+1, 0
                if rem or r1 != r2:
                    self.assertEqual(rem, 0, '%s != 0 for %s' % (rem, i))
                    self.assertEqual(r1, r2, '%s != %s for %s' % (r1, r2, i))

    def test_startswith(self):
        self.checkequal(True, 'hello', 'startswith', 'he')
        self.checkequal(True, 'hello', 'startswith', 'hello')
        self.checkequal(False, 'hello', 'startswith', 'hello world')
        self.checkequal(True, 'hello', 'startswith', '')
        self.checkequal(False, 'hello', 'startswith', 'ello')
        self.checkequal(True, 'hello', 'startswith', 'ello', 1)
        self.checkequal(True, 'hello', 'startswith', 'o', 4)
        self.checkequal(False, 'hello', 'startswith', 'o', 5)
        self.checkequal(True, 'hello', 'startswith', '', 5)
        self.checkequal(False, 'hello', 'startswith', 'lo', 6)
        self.checkequal(True, 'helloworld', 'startswith', 'lowo', 3)
        self.checkequal(True, 'helloworld', 'startswith', 'lowo', 3, 7)
        self.checkequal(False, 'helloworld', 'startswith', 'lowo', 3, 6)
        self.checkequal(True, '', 'startswith', '', 0, 1)
        self.checkequal(True, '', 'startswith', '', 0, 0)
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            self.checkequal(False, '', 'startswith', '', 1, 0)

        # test negative indices
        self.checkequal(True, 'hello', 'startswith', 'he', 0, -1)
        self.checkequal(True, 'hello', 'startswith', 'he', -53, -1)
        self.checkequal(False, 'hello', 'startswith', 'hello', 0, -1)
        self.checkequal(False, 'hello', 'startswith', 'hello world', -1, -10)
        self.checkequal(False, 'hello', 'startswith', 'ello', -5)
        self.checkequal(True, 'hello', 'startswith', 'ello', -4)
        self.checkequal(False, 'hello', 'startswith', 'o', -2)
        self.checkequal(True, 'hello', 'startswith', 'o', -1)
        self.checkequal(True, 'hello', 'startswith', '', -3, -3)
        self.checkequal(False, 'hello', 'startswith', 'lo', -9)

        self.checkraises(TypeError, 'hello', 'startswith')
        #self.checkraises(TypeError, 'hello', 'startswith', 42)

        # test tuple arguments
        self.checkequal(True, 'hello', 'startswith', ('he', 'ha'))
        self.checkequal(False, 'hello', 'startswith', ('lo', 'llo'))
        self.checkequal(True, 'hello', 'startswith', ('hellox', 'hello'))
        self.checkequal(False, 'hello', 'startswith', ())
        self.checkequal(True, 'helloworld', 'startswith', ('hellowo',
                                                           'rld', 'lowo'), 3)
        self.checkequal(False, 'helloworld', 'startswith', ('hellowo', 'ello',
                                                            'rld'), 3)
        self.checkequal(True, 'hello', 'startswith', ('lo', 'he'), 0, -1)
        self.checkequal(False, 'hello', 'startswith', ('he', 'hel'), 0, 1)
        self.checkequal(True, 'hello', 'startswith', ('he', 'hel'), 0, 2)

        self.checkraises(TypeError, 'hello', 'startswith', (42,))
        self.checkequal(True, 'hello', 'startswith', ('he', 42))
        self.checkraises(TypeError, 'hello', 'startswith', ('ne', 42,))

    def test_rsplit(self):
        # by a char
        self.checkequal(['a', 'b', 'c', 'd'], 'a|b|c|d', 'rsplit', '|')
        self.checkequal(['a|b|c', 'd'], 'a|b|c|d', 'rsplit', '|', 1)
        self.checkequal(['a|b', 'c', 'd'], 'a|b|c|d', 'rsplit', '|', 2)
        self.checkequal(['a', 'b', 'c', 'd'], 'a|b|c|d', 'rsplit', '|', 3)
        self.checkequal(['a', 'b', 'c', 'd'], 'a|b|c|d', 'rsplit', '|', 4)
        self.checkequal(['a', 'b', 'c', 'd'], 'a|b|c|d', 'rsplit', '|',
                        sys.maxsize-100)
        self.checkequal(['a|b|c|d'], 'a|b|c|d', 'rsplit', '|', 0)
        self.checkequal(['a||b||c', '', 'd'], 'a||b||c||d', 'rsplit', '|', 2)
        self.checkequal(['abcd'], 'abcd', 'rsplit', '|')
        self.checkequal([''], '', 'rsplit', '|')
        self.checkequal(['', ' begincase'], '| begincase', 'rsplit', '|')
        self.checkequal(['endcase ', ''], 'endcase |', 'rsplit', '|')
        self.checkequal(['', 'bothcase', ''], '|bothcase|', 'rsplit', '|')

        self.checkequal(['a\x00\x00b', 'c', 'd'], 'a\x00\x00b\x00c\x00d', 'rsplit', '\x00', 2)

        self.checkequal(['a']*20, ('a|'*20)[:-1], 'rsplit', '|')
        self.checkequal(['a|a|a|a|a']+['a']*15,
                        ('a|'*20)[:-1], 'rsplit', '|', 15)

        # by string
        self.checkequal(['a', 'b', 'c', 'd'], 'a//b//c//d', 'rsplit', '//')
        self.checkequal(['a//b//c', 'd'], 'a//b//c//d', 'rsplit', '//', 1)
        self.checkequal(['a//b', 'c', 'd'], 'a//b//c//d', 'rsplit', '//', 2)
        self.checkequal(['a', 'b', 'c', 'd'], 'a//b//c//d', 'rsplit', '//', 3)
        self.checkequal(['a', 'b', 'c', 'd'], 'a//b//c//d', 'rsplit', '//', 4)
        self.checkequal(['a', 'b', 'c', 'd'], 'a//b//c//d', 'rsplit', '//',
                        sys.maxsize-5)
        self.checkequal(['a//b//c//d'], 'a//b//c//d', 'rsplit', '//', 0)
        self.checkequal(['a////b////c', '', 'd'], 'a////b////c////d', 'rsplit', '//', 2)
        self.checkequal(['', ' begincase'], 'test begincase', 'rsplit', 'test')
        self.checkequal(['endcase ', ''], 'endcase test', 'rsplit', 'test')
        self.checkequal(['', ' bothcase ', ''], 'test bothcase test',
                        'rsplit', 'test')
        self.checkequal(['ab', 'c'], 'abbbc', 'rsplit', 'bb')
        self.checkequal(['', ''], 'aaa', 'rsplit', 'aaa')
        self.checkequal(['aaa'], 'aaa', 'rsplit', 'aaa', 0)
        self.checkequal(['ab', 'ab'], 'abbaab', 'rsplit', 'ba')
        self.checkequal(['aaaa'], 'aaaa', 'rsplit', 'aab')
        self.checkequal([''], '', 'rsplit', 'aaa')
        self.checkequal(['aa'], 'aa', 'rsplit', 'aaa')
        self.checkequal(['bbob', 'A'], 'bbobbbobbA', 'rsplit', 'bbobb')
        self.checkequal(['', 'B', 'A'], 'bbobbBbbobbA', 'rsplit', 'bbobb')

        self.checkequal(['a']*20, ('aBLAH'*20)[:-4], 'rsplit', 'BLAH')
        self.checkequal(['a']*20, ('aBLAH'*20)[:-4], 'rsplit', 'BLAH', 19)
        self.checkequal(['aBLAHa'] + ['a']*18, ('aBLAH'*20)[:-4],
                        'rsplit', 'BLAH', 18)

        # with keyword args
        self.checkequal(['a', 'b', 'c', 'd'], 'a|b|c|d', 'rsplit', sep='|')
        self.checkequal(['a|b|c', 'd'],
                        'a|b|c|d', 'rsplit', '|', maxsplit=1)
        self.checkequal(['a|b|c', 'd'],
                        'a|b|c|d', 'rsplit', sep='|', maxsplit=1)
        self.checkequal(['a|b|c', 'd'],
                        'a|b|c|d', 'rsplit', maxsplit=1, sep='|')
        self.checkequal(['a b c', 'd'],
                        'a b c d', 'rsplit', maxsplit=1)

        # argument type
        self.checkraises(TypeError, 'hello', 'rsplit', 42, 42, 42)

        # null case
        self.checkraises(ValueError, 'hello', 'rsplit', '')
        self.checkraises(ValueError, 'hello', 'rsplit', '', 0)


def test_same_id():
    empty_ids = set([id(str()) for i in range(100)])
    assert len(empty_ids) == 1
    empty_ids = set([id('') for i in range(100)])
    assert len(empty_ids) == 1
    empty_ids = set([id(u'') for i in range(100)])
    assert len(empty_ids) == 1


def test_translate():
    assert "abc".translate({ord("a"): "b"}) == "bbc"
    assert "abc".translate({ord("a"): "xxx"}) == "xxxbc"
    assert "abc".translate({ord("a"): ""}) == "bc"
    try:
        "abc".translate({ord("a"): 8**63})
    except (ValueError, TypeError) as e:
        assert "mapping must be in range" in str(e)
    else:
        assert False, "should raise"


def test_translate_from_byte_table():
    table = bytes.maketrans(bytes(string.ascii_lowercase, 'ascii'), bytes(string.ascii_uppercase, 'ascii'))
    assert "ahoj".translate(table) == "AHOJ"
    assert "ahoj".translate(bytearray(table)) == "AHOJ"
    assert "ahoj".translate(memoryview(table)) == "AHOJ"


def test_tranlslate_from_short_table():
    table = b'\x00\x01\x02\x03\x04\x05\x06\x07\x08\t\n\x0b\x0c\r\x0e\x0f\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f !"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`ABCDEFGH'
    assert "ahoj".translate(table) == "AHoj"


def test_translate_nonascii_from_byte_table():
    table = bytes.maketrans(bytes(string.ascii_lowercase, 'ascii'), bytes(string.ascii_uppercase, 'ascii'))
    assert "ačhřožj".translate(table) == "AčHřOžJ"


def test_translate_from_long_byte_table():
    table = bytes.maketrans(bytes(string.ascii_lowercase, 'ascii'), bytes(string.ascii_uppercase, 'ascii'))
    table *= 30
    assert 'ahoj453875287ščřžýáí'.translate(table) == 'AHOJ453875287A\rY~ýáí'


def test_splitlines():
    assert len(str.splitlines("\n\n")) == 2
    assert len(str.splitlines("\n")) == 1
    assert len(str.splitlines("a\nb")) == 2


def test_literals():
    s = "hello\[world\]"
    assert len(s) == 14
    assert "hello\[world\]"[5] == "\\"
    assert "hello\[world\]"[6] == "["
    assert "hello\[world\]"[12] == "\\"
    assert "hello\[world\]"[13] == "]"


def test_strip_whitespace():
    assert 'hello' == '   hello   '.strip()
    assert 'hello   ' == '   hello   '.lstrip()
    assert '   hello' == '   hello   '.rstrip()
    assert 'hello' == 'hello'.strip()

    b = ' \t\n\r\f\vabc \t\n\r\f\v'
    assert 'abc' == b.strip()
    assert 'abc \t\n\r\f\v' == b.lstrip()
    assert ' \t\n\r\f\vabc' == b.rstrip()

    # strip/lstrip/rstrip with None arg
    assert 'hello' == '   hello   '.strip(None)
    assert 'hello   ' == '   hello   '.lstrip(None)
    assert '   hello' == '   hello   '.rstrip(None)
    assert 'hello' == 'hello'.strip(None)


def test_strip_with_sep():
    # strip/lstrip/rstrip with str arg
    assert 'hello' == 'xyzzyhelloxyzzy'.strip('xyz')
    assert 'helloxyzzy' == 'xyzzyhelloxyzzy'.lstrip('xyz')
    assert 'xyzzyhello' == 'xyzzyhelloxyzzy'.rstrip('xyz')
    assert 'hello' == 'hello'.strip('xyz')
    assert '' == 'mississippi'.strip('mississippi')

    # only trim the start and end; does not strip internal characters
    assert 'mississipp' == 'mississippi'.strip('i')

    assertRaises(TypeError, 'hello', 'strip', 42, 42)
    assertRaises(TypeError, 'hello', 'lstrip', 42, 42)
    assertRaises(TypeError, 'hello', 'rstrip', 42, 42)
