# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import sys

def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


# empty sequence
def test_empty_sequence():
    b = bytes()
    assert type(b) == bytes
    assert b.__class__ == bytes
    assert_raises(IndexError, lambda: b[0])
    assert_raises(IndexError, lambda: b[1])
    assert_raises(IndexError, lambda: b[10 ** 100])
    assert_raises(IndexError, lambda: b[-1])
    assert_raises(IndexError, lambda: b[-2])
    assert_raises(IndexError, lambda: b[-10 ** 100])


def test_from_list():
    ints = list(range(256))
    b = bytes(i for i in ints)
    assert len(b) == 256
    assert list(b) == ints


def test_from_ssize():
    assert bytes(0) == b''
    assert bytes(1) == b'\x00'
    assert bytes(5) == b'\x00\x00\x00\x00\x00'
    assert bytes(1) == bytes([0])
    assert bytes(5) == bytes([0, 0, 0, 0, 0])
    assert_raises(ValueError, bytes, -1)
    assert bytes('0', 'ascii') == b'0'
    assert bytes(b'0') == b'0'
    # assert_raises(OverflowError, bytes, sys.maxsize + 1)
    # TODO: add maxsize to sys
    assert_raises(OverflowError, bytes, 9223372036854775808)


def test_constructor_value_errors():
    assert_raises(ValueError, bytes, [-1])
    # assert_raises(ValueError, bytes, [-sys.maxsize])
    # assert_raises(ValueError, bytes, [-sys.maxsize-1])
    # assert_raises(ValueError, bytes, [-sys.maxsize-2])
    # assert_raises(ValueError, bytes, [-10**100])
    assert_raises(ValueError, bytes, [256])
    assert_raises(ValueError, bytes, [257])
    # assert_raises(ValueError, bytes, [sys.maxsize])
    # assert_raises(ValueError, bytes, [sys.maxsize+1])
    # assert_raises(ValueError, bytes, [10**100])


def test_reverse():
    b = bytearray(b'hello')
    assert b.reverse() is None
    assert b == b'olleh'
    b = bytearray(b'hello1')  # test even number of items
    b.reverse()
    assert b == b'1olleh'
    b = bytearray()
    assert not b
    b.reverse()
    assert not b


def test_clear():
    b = bytearray(b'python')
    b.clear()
    assert b == b''

    b = bytearray(b'')
    b.clear()
    assert b == b''

    b = bytearray(b'')
    b.append(ord('r'))
    b.clear()
    b.append(ord('p'))
    assert b == b'p'

    b = bytearray(range(10))
    assert len(b) == 10
    del b[:]
    assert len(b) == 0
    assert b == b''


def test_copy():
    b = bytearray(b'abc')
    bb = b.copy()
    assert bb == b'abc'

    b = bytearray(b'')
    bb = b.copy()
    assert bb == b''

    # test that it's indeed a copy and not a reference
    b = bytearray(b'abc')
    bb = b.copy()
    assert b == bb
    assert b is not bb
    bb.append(ord('d'))
    assert bb == b'abcd'
    assert b == b'abc'


def test_setitem():
    b = bytearray([1, 2, 3])
    b[1] = 100
    assert b == bytearray([1, 100, 3])
    b[-1] = 200
    assert b == bytearray([1, 100, 200])
    # b[0] = Indexable(10)
    # assert b == bytearray([10, 100, 200])
    try:
        b[3] = 0
        assert False, "Didn't raise IndexError"
    except IndexError:
        pass
    try:
        b[-10] = 0
        assert False, "Didn't raise IndexError"
    except IndexError:
        pass
    try:
        b[0] = 256
        assert False, "Didn't raise ValueError"
    except ValueError:
        pass
    # try:
    #     b[0] = Indexable(-1)
    #     assert False, "Didn't raise ValueError"
    # except ValueError:
    #     pass
    try:
        b[0] = None
        assert False, "Didn't raise TypeError"
    except TypeError:
        pass


def test_setslice():
    # whole sequence
    b = bytearray(b"hello")
    b[0:5] = b"HELLO"
    assert b == bytearray(b"HELLO")

    # whole same length as slice
    b = bytearray(b"hellohellohello")
    b[5:10] = b"HELLO"
    assert b == bytearray(b"helloHELLOhello")

    # shrink
    b = bytearray(b"hellohellohello")
    b[5:10] = b"hi"
    assert b == bytearray(b"hellohihello")

    # extend
    b = bytearray(b"hellohelloworld")
    b[5:10] = b"beautiful"
    assert b == bytearray(b"hellobeautifulworld")

    # assign list with integers
    b = bytearray(b"hellohellohello")
    b[5:10] = [4, 5, 6, 7, 8]
    assert b == bytearray(b"hello\x04\x05\x06\x07\x08hello")

    # assign range
    b = bytearray(b"hellohellohello")
    b[5:10] = range(5)
    assert b == bytearray(b'hello\x00\x01\x02\x03\x04hello')

    b = bytearray(range(10))
    assert list(b) == list(range(10))

    b[0:5] = bytearray([1, 1, 1, 1, 1])
    assert b == bytearray([1, 1, 1, 1, 1, 5, 6, 7, 8, 9])

    # TODO: seq storage does not yet support deletion ...
    # del b[0:-5]
    # assert b == bytearray([5, 6, 7, 8, 9])
    b = bytearray([5, 6, 7, 8, 9])

    b[0:0] = bytearray([0, 1, 2, 3, 4])
    assert b == bytearray(range(10))
    b = bytearray(range(10))

    b[-7:-3] = bytearray([100, 101])
    assert b == bytearray([0, 1, 2, 100, 101, 7, 8, 9])

    b[3:5] = [3, 4, 5, 6]
    assert b == bytearray(range(10))

    b[3:0] = [42, 42, 42]
    assert b == bytearray([0, 1, 2, 42, 42, 42, 3, 4, 5, 6, 7, 8, 9])

    b[3:] = b'foo'
    assert b == bytearray([0, 1, 2, 102, 111, 111])

    b[:3] = memoryview(b'foo')
    assert b == bytearray([102, 111, 111, 102, 111, 111])

    b[3:4] = []
    assert b == bytearray([102, 111, 111, 111, 111])

    for elem in [5, -5, 0, int(10e20), 'str', 2.3,
                 ['a', 'b'], [b'a', b'b'], [[]]]:
        def assign():
            b[3:4] = elem
        assert_raises(TypeError, assign)

    for elem in [[254, 255, 256], [-256, 9000]]:
        def assign():
            b[3:4] = elem
        assert_raises(ValueError, assign)


def test_delitem():
    b = bytearray(range(10))
    del b[0]
    assert b == bytearray(range(1, 10))
    del b[-1]
    assert b == bytearray(range(1, 9))
    del b[4]
    assert b == bytearray([1, 2, 3, 4, 6, 7, 8])
    b = bytearray(range(10))
    del b[0:10]
    assert b == bytearray()
    b = bytearray(range(10))
    del b[0:10000]
    assert b == bytearray()
    b = bytearray(range(10))
    del b[0:-10000]
    assert b == bytearray(range(10))
    b = bytearray(range(10))
    del b[-1000:1000]
    assert b == bytearray()


def test_subclass():

    class MyByteArray(bytearray):

        def __str__(self):
            return "<<%s>>" % super(MyByteArray, self).__str__()

    b1 = bytearray(range(10))
    b2 = MyByteArray(range(10))
    assert b1 == b2
    assert "<<%s>>" % str(b1) == str(b2)

    class MyBytes(bytes):

        def __str__(self):
            return "[[%s]]" % super(MyBytes, self).__str__()

    b3 = b'python'
    b4 = MyBytes(b'python')
    assert b3 == b4
    assert "[[%s]]" % str(b3) == str(b4)


def test_eq():
    assert bytearray.__eq__(bytearray(b'1'), b'1')
    assert NotImplemented == bytearray.__eq__(bytearray(b'1'), '1')
    assert_raises(TypeError, lambda: bytearray.__eq__('1', bytearray(b'1')))
    assert bytes.__eq__(b'1', bytearray(b'1'))
    assert NotImplemented == bytes.__eq__(b'1', '1')
    assert_raises(TypeError, lambda: bytes.__eq__('1', bytearray(b'1')))


class ByteArraySubclass(bytearray):
    pass


class BytesSubclass(bytes):
    pass


def _test_join(basetype, type2test):
    s1 = type2test(b"abcd")
    s2 = basetype().join([s1])
    assert s1 is not s2
    assert type(s2) is basetype

    # Test reverse, calling join on subclass
    s3 = s1.join([b"abcd"])
    assert type(s3) is basetype


def test_join():
    _test_join(bytes, BytesSubclass)
    _test_join(bytearray, ByteArraySubclass)
    assert b"--".join([]) == b""
    assert b"--".join([b"hello"]) == b"hello"


def test_concat():
    a = b'0'
    b = b'1'
    c = bytearray([0])
    d = bytearray([1])

    assert a + b == b'01'
    assert a + d == b'0\x01'
    assert c + d == bytearray([0, 1])
    assert c + b == bytearray([0, ord('1')])
    assert_raises(TypeError, lambda x, y: x + y, a, 1)
    assert_raises(TypeError, lambda x, y: x + y, a, "1")
    assert_raises(TypeError, lambda x, y: x + y, a, [1])
    assert_raises(TypeError, lambda x, y: x + y, a, object())
    assert_raises(TypeError, lambda x, y: x + y, c, 1)
    assert_raises(TypeError, lambda x, y: x + y, c, "1")
    assert_raises(TypeError, lambda x, y: x + y, c, [1])
    assert_raises(TypeError, lambda x, y: x + y, c, object())


def test_mul():
    a = b'01'

    assert a * 3 == b'010101'
    assert 3 * a == b'010101'

    a = bytearray([0, 1])

    assert a * 3 == bytearray([0, 1, 0, 1, 0, 1])
    assert 3 * a == bytearray([0, 1, 0, 1, 0, 1])


def test_contains():
    assert b"/" in b"/", "b'/' was not in b'/'"
    assert bytearray([32]) in b" ", "bytearray([32]) was not in b' '"
    assert_raises(TypeError, lambda: "/" in b"/")


def test_count():
    assert b"hello".count(b"l") == 2, "1"
    assert b"hello".count(b"h") == 1, "2"
    assert b"hello".count(b"ll") == 1, "3"
    assert b"hellohello".count(b"ll") == 2, "4"
    assert b"hellohello".count(b"ll", 5) == 1, "5"


def test_rfind():
    assert b"".rfind(b"") == 0, "1"
    assert b"hello".rfind(b"") == 5, "2"
    assert b"hello".rfind(b"l") == 3, "3"
    assert b"hello".rfind(b"x") == -1, "4"
    assert b"hello".rfind(b"ll") == 2, "3"


def test_extend():
    orig = b'hello'
    a = bytearray(orig)
    a.extend(a)

    assert a == orig + orig
    assert a[5:] == orig

    a = bytearray(b'')
    # Test iterators that don't have a __length_hint__
    a.extend(map(int, orig * 25))
    a.extend(int(x) for x in orig * 25)
    assert a == orig * 50
    assert a[-5:] == orig

    a = bytearray(b'')
    a.extend(iter(map(int, orig * 50)))
    assert a == orig * 50
    assert a[-5:] == orig

    a = bytearray(b'')
    a.extend(list(map(int, orig * 50)))
    assert a == orig * 50
    assert a[-5:] == orig

    a = bytearray(b'')
    assert_raises(ValueError, a.extend, [0, 1, 2, 256])
    assert_raises(ValueError, a.extend, [0, 1, 2, -1])
    assert len(a) == 0


def test_startswith():
    b = b'hello'
    assert not b.startswith(b"anything")
    assert b.startswith(b"hello")
    assert b.startswith(b"hel")
    assert b.startswith(b"h")
    assert not b.startswith(b"hellow")
    assert not b.startswith(b"ha")

    b = bytearray(b'hello')
    assert not b.startswith(b"anything")
    assert b.startswith(b"hello")
    assert b.startswith(b"hel")
    assert b.startswith(b"h")
    assert not b.startswith(b"hellow")
    assert not b.startswith(b"ha")


def test_endswith():
    b = b'hello'
    assert not b.endswith(b"anything")
    assert b.endswith(b"hello")
    assert b.endswith(b"llo")
    assert b.endswith(b"o")
    assert not b.endswith(b"whello")
    assert not b.endswith(b"no")

    b = bytearray(b'hello')
    assert not b.endswith(b"anything")
    assert b.endswith(b"hello")
    assert b.endswith(b"llo")
    assert b.endswith(b"o")
    assert not b.endswith(b"whello")
    assert not b.endswith(b"no")


def test_ord():
    b = b"123"
    assert ord(b[0:1]) == 49
    assert ord(bytearray(b)[0:1]) == 49
    try:
        ord(b)
    except TypeError as e:
        assert "expected a character" in str(e), str(e)
    else:
        assert False


def test_find():
    b = b'mississippi'
    i = 105
    w = 119

    assert b.find(b'ss') == 2
    assert b.find(b'w') == -1
    assert b.find(b'mississippian') == -1

    assert b.find(i) == 1
    assert b.find(w) == -1

    assert b.find(b'ss', 3) == 5
    assert b.find(b'ss', 1, 7) == 2
    assert b.find(b'ss', 1, 3) == -1

    assert b.find(i, 6) == 7
    assert b.find(i, 1, 3) == 1
    assert b.find(w, 1, 3) == -1

    ba = bytearray(b'mississippi')
    i = 105
    w = 119

    assert ba.find(b'ss') == 2
    assert ba.find(b'w') == -1
    assert ba.find(b'mississippian') == -1

    assert ba.find(i) == 1
    assert ba.find(w) == -1

    assert ba.find(b'ss', 3) == 5
    assert ba.find(b'ss', 1, 7) == 2
    assert ba.find(b'ss', 1, 3) == -1

    assert ba.find(i, 6) == 7
    assert ba.find(i, 1, 3) == 1
    assert ba.find(w, 1, 3) == -1

    try:
        res = ba.find("ss")
    except TypeError:
        assert True
    else:
        assert False, "should not reach here"


def test_same_id():
    empty_ids = set([id(bytes()) for i in range(100)])
    assert len(empty_ids) == 1


def test_binary_op():
    assert not (bytes(memoryview(b"123")) != memoryview(b"123").tobytes())
    assert bytes(memoryview(b"123")) == memoryview(b"123").tobytes()
    assert b"123" < b"1234"
    assert not (b"153" < b"1234")
    assert not (b"123" > b"1234")
    assert b"153" > b"1234"
    assert b"123" <= b"123"
    assert b"123" <= b"1234"
    assert b"123" >= b"123"
    assert not (b"123" >= b"1234")


def test_strip_bytearray():
    assert bytearray(b'abc').strip(b'ac') == b'b'
    assert bytearray(b'abc').lstrip(b'ac') == b'bc'
    assert bytearray(b'abc').rstrip(b'ac') == b'ab'

def test_strip_bytes():
    assert b'abc'.strip(b'ac') == b'b'
    assert b'abc'.lstrip(b'ac') == b'bc'
    assert b'abc'.rstrip(b'ac') == b'ab'

class BaseTestSplit:

    def test_string_error(self):
        self.assertRaises(TypeError, self.type2test(b'a b').split, ' ')
        self.assertRaises(TypeError, self.type2test(b'a b').rsplit, ' ')

    def test_int_error(self):
        self.assertRaises(TypeError, self.type2test(b'a b').split, 32)
        self.assertRaises(TypeError, self.type2test(b'a b').rsplit, 32)

    def test_split_unicodewhitespace(self):
        for b in (b'a\x1Cb', b'a\x1Db', b'a\x1Eb', b'a\x1Fb'):
            b = self.type2test(b)
            self.assertEqual(b.split(), [b])
        b = self.type2test(b"\x09\x0A\x0B\x0C\x0D\x1C\x1D\x1E\x1F")
        self.assertEqual(b.split(), [b'\x1c\x1d\x1e\x1f'])

    def test_rsplit_unicodewhitespace(self):
        b = self.type2test(b"\x09\x0A\x0B\x0C\x0D\x1C\x1D\x1E\x1F")
        self.assertEqual(b.rsplit(), [b'\x1c\x1d\x1e\x1f'])

    def test_memoryview(self):
        self.assertEqual(self.type2test(b'a b').split(memoryview(b' ')), [b'a', b'b'])
        self.assertEqual(self.type2test(b'c d').rsplit(memoryview(b' ')), [b'c', b'd'])

    def test_split(self):
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').split(), [b'ahoj', b'jak', b'se', b'mas'])
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').rsplit(), [b'ahoj', b'jak', b'se', b'mas'])

    def test_maxsplit(self):
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').split(maxsplit=1), [b'ahoj', b'jak\tse\nmas'])
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').rsplit(maxsplit=1), [b'ahoj jak\tse', b'mas'])

    def test_maxsplit_zero(self):
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').split(maxsplit=0), [b'ahoj jak\tse\nmas'])
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').rsplit(maxsplit=0), [b'ahoj jak\tse\nmas'])

    def test_maxsplit_negative(self):
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').split(maxsplit=-10), [b'ahoj', b'jak', b'se', b'mas'])
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').rsplit(maxsplit=-10), [b'ahoj', b'jak', b'se', b'mas'])

    def test_separator(self):
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').split(b' '), [b'ahoj', b'jak\tse\nmas'])
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').rsplit(b' '), [b'ahoj', b'jak\tse\nmas'])

    def test_empty(self):
        self.assertEqual(self.type2test(b'').split(), [])
        self.assertEqual(self.type2test(b'').rsplit(), [])

    def test_empty_delim(self):
        self.assertEqual(self.type2test(b'').split(b' '), [b''])
        self.assertEqual(self.type2test(b'').rsplit(b' '), [b''])

    def test_empty_separator(self):
        self.assertRaises(ValueError, self.type2test(b'a b').split, b'')
        self.assertRaises(ValueError, self.type2test(b'a b').rsplit, b'')

    def test_indexable_object(self):

        class MyIndexable(object):
            def __init__(self, value):
                self.value = value
            def __index__(self):
                return self.value

        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').split(maxsplit=MyIndexable(1)), [b'ahoj', b'jak\tse\nmas'])
        self.assertEqual(self.type2test(b'ahoj jak\tse\nmas').rsplit(maxsplit=MyIndexable(1)), [b'ahoj jak\tse', b'mas'])

class BytesSplitTest(BaseTestSplit, unittest.TestCase):
    type2test = bytes

class ByteArraySplitTest(BaseTestSplit, unittest.TestCase):
    type2test = bytearray


def test_eq_add_bytearray():
    b1 = bytearray(b'')
    b2 = b1
    b1 += bytearray(b'Hello')
    assert b1 is b2


def test_add_mv_to_bytes():
    b = b'hello '
    mv = memoryview(b'world')
    b += mv
    assert b == b'hello world'

def test_add_mv_to_bytearray():
    ba = bytearray(b'hello ')
    mv = memoryview(b'world')
    ba += mv
    assert ba == b'hello world'

class BaseLikeBytes:

    def test_maketrans(self):
        transtable = b'\000\001\002\003\004\005\006\007\010\011\012\013\014\015\016\017\020\021\022\023\024\025\026\027\030\031\032\033\034\035\036\037 !"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`xyzdefghijklmnopqrstuvwxyz{|}~\177\200\201\202\203\204\205\206\207\210\211\212\213\214\215\216\217\220\221\222\223\224\225\226\227\230\231\232\233\234\235\236\237\240\241\242\243\244\245\246\247\250\251\252\253\254\255\256\257\260\261\262\263\264\265\266\267\270\271\272\273\274\275\276\277\300\301\302\303\304\305\306\307\310\311\312\313\314\315\316\317\320\321\322\323\324\325\326\327\330\331\332\333\334\335\336\337\340\341\342\343\344\345\346\347\350\351\352\353\354\355\356\357\360\361\362\363\364\365\366\367\370\371\372\373\374\375\376\377'
        self.assertEqual(self.type2test.maketrans(b'abc', b'xyz'), transtable)
        transtable = b'\000\001\002\003\004\005\006\007\010\011\012\013\014\015\016\017\020\021\022\023\024\025\026\027\030\031\032\033\034\035\036\037 !"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\177\200\201\202\203\204\205\206\207\210\211\212\213\214\215\216\217\220\221\222\223\224\225\226\227\230\231\232\233\234\235\236\237\240\241\242\243\244\245\246\247\250\251\252\253\254\255\256\257\260\261\262\263\264\265\266\267\270\271\272\273\274\275\276\277\300\301\302\303\304\305\306\307\310\311\312\313\314\315\316\317\320\321\322\323\324\325\326\327\330\331\332\333\334\335\336\337\340\341\342\343\344\345\346\347\350\351\352\353\354\355\356\357\360\361\362\363\364\365\366\367\370\371\372\373\374xyz'
        self.assertEqual(self.type2test.maketrans(b'\375\376\377', b'xyz'), transtable)
        self.assertRaises(ValueError, self.type2test.maketrans, b'abc', b'xyzq')
        self.assertRaises(TypeError, self.type2test.maketrans, 'abc', 'def')

    def test_translate(self):
        b = self.type2test(b'hello')
        rosetta = bytearray(range(256))
        rosetta[ord('o')] = ord('e')

        self.assertRaises(TypeError, b.translate)
        self.assertRaises(TypeError, b.translate, None, None)
        self.assertRaises(ValueError, b.translate, bytes(range(255)))

        c = b.translate(rosetta, b'hello')
        self.assertEqual(b, b'hello')
        self.assertIsInstance(c, self.type2test)

        c = b.translate(rosetta)
        d = b.translate(rosetta, b'')
        self.assertEqual(c, d)
        self.assertEqual(c, b'helle')

        c = b.translate(rosetta, b'l')
        self.assertEqual(c, b'hee')

        c = b.translate(None, b'e')
        self.assertEqual(c, b'hllo')

        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            # test delete as a keyword argument
            c = b.translate(rosetta, delete=b'')
            self.assertEqual(c, b'helle')
            c = b.translate(rosetta, delete=b'l')
            self.assertEqual(c, b'hee')
            c = b.translate(None, delete=b'e')
            self.assertEqual(c, b'hllo')

class BytesTest(BaseLikeBytes, unittest.TestCase):
    type2test = bytes

    def test_translate_no_change(self):
        b = b'ahoj'
        self.assertIs(b, b.translate(None))
        self.assertIs(b, b.translate(None, b''))
        table = bytearray(range(256))
        self.assertIs(b, b.translate(table))
        self.assertIs(b, b.translate(table), b'')
        self.assertIs(b, b.translate(table), b'klp')

class ByteArrayTest(BaseLikeBytes, unittest.TestCase):
    type2test = bytearray

    def test_translate_no_change2(self):
        b = bytearray(b'ahoj')
        self.assertIsNot(b, b.translate(None))
        self.assertIsNot(b, b.translate(None, b''))
        table = bytearray(range(256))
        self.assertIsNot(b, b.translate(table))
        self.assertIsNot(b, b.translate(table), b'')
        self.assertIsNot(b, b.translate(table), b'klp')
