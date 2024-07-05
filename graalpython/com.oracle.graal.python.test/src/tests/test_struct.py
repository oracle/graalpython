# Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import struct
import sys


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


ISBIGENDIAN = sys.byteorder == "big"


def test_new_features():
    tests = [
        ('c', b'a', b'a', b'a', 0),
        ('xc', b'a', b'\0a', b'\0a', 0),
        ('cx', b'a', b'a\0', b'a\0', 0),
        ('s', b'a', b'a', b'a', 0),
        ('0s', b'helloworld', b'', b'', 1),
        ('1s', b'helloworld', b'h', b'h', 1),
        ('9s', b'helloworld', b'helloworl', b'helloworl', 1),
        ('10s', b'helloworld', b'helloworld', b'helloworld', 0),
        ('11s', b'helloworld', b'helloworld\0', b'helloworld\0', 1),
        ('20s', b'helloworld', b'helloworld'+10*b'\0', b'helloworld'+10*b'\0', 1),
        ('b', 7, b'\7', b'\7', 0),
        ('b', -7, b'\371', b'\371', 0),
        ('B', 7, b'\7', b'\7', 0),
        ('B', 249, b'\371', b'\371', 0),
        ('h', 700, b'\002\274', b'\274\002', 0),
        ('h', -700, b'\375D', b'D\375', 0),
        ('H', 700, b'\002\274', b'\274\002', 0),
        ('H', 0x10000-700, b'\375D', b'D\375', 0),
        ('i', 70000000, b'\004,\035\200', b'\200\035,\004', 0),
        ('i', -70000000, b'\373\323\342\200', b'\200\342\323\373', 0),
        ('I', 70000000, b'\004,\035\200', b'\200\035,\004', 0),
        ('I', 0x100000000-70000000, b'\373\323\342\200', b'\200\342\323\373', 0),
        ('l', 70000000, b'\004,\035\200', b'\200\035,\004', 0),
        ('l', -70000000, b'\373\323\342\200', b'\200\342\323\373', 0),
        ('L', 70000000, b'\004,\035\200', b'\200\035,\004', 0),
        ('L', 0x100000000-70000000, b'\373\323\342\200', b'\200\342\323\373', 0),
        ('f', 2.0, b'@\000\000\000', b'\000\000\000@', 0),
        ('d', 2.0, b'@\000\000\000\000\000\000\000',
         b'\000\000\000\000\000\000\000@', 0),
        ('f', -2.0, b'\300\000\000\000', b'\000\000\000\300', 0),
        ('d', -2.0, b'\300\000\000\000\000\000\000\000',
         b'\000\000\000\000\000\000\000\300', 0),
        ('?', 0, b'\0', b'\0', 0),
        ('?', 3, b'\1', b'\1', 1),
        ('?', True, b'\1', b'\1', 0),
        ('?', [], b'\0', b'\0', 1),
        ('?', (1,), b'\1', b'\1', 1),
    ]

    for fmt, arg, big, lil, asy in tests:
        for (xfmt, exp) in [('>'+fmt, big), ('!'+fmt, big), ('<'+fmt, lil),
                            ('='+fmt, ISBIGENDIAN and big or lil)]:
            res = struct.pack(xfmt, arg)
            assert res == exp
            assert struct.calcsize(xfmt) == len(res)
            rev = struct.unpack(xfmt, res)[0]
            if rev != arg:
                assert asy


def test_pack_unpack():
    # numbers
    cases = [
        ((60, 61, 62, 12365, 3454353, 75, 76), '3BHI2B'),
        ((9223372036854775806,), 'Q'),
    ]

    alignment = ['>', '<', '!', '=', '@']

    for vals, fmt in cases:
        for align in alignment:
            _fmt = align + fmt
            result = struct.pack(_fmt, *vals)
            assert vals == struct.unpack(_fmt, result), "{} != struct.unpack({}, {})".format(vals, _fmt, result)
            assert struct.calcsize(_fmt) == len(result), "calcsize('{}')={} != len({})={}".format(
                _fmt, struct.calcsize(_fmt), result, len(result))

    # bytes / strings
    long_str = b'hello-graal-python-hello-graal-python-hello-graal-python-hello-graal-python-hello-graal-python-hello-' \
               b'graal-python-hello-graal-python-hello-graal-python-hello-graal-python-hello-graal-python-hello-graal-' \
               b'python-hello-graal-python-hello-graal-python-hello-graal-p'

    pascal_str = b'\xffhello-graal-python-hello-graal-python-hello-graal-python-hello-graal-python-hello-graal-python-' \
                 b'hello-graal-python-hello-graal-python-hello-graal-python-hello-graal-python-hello-graal-python-' \
                 b'hello-graal-python-hello-graal-python-hello-graal-python-hello-graal-'

    for align in alignment:
        for (res, fmt) in [(long_str, 's'), (pascal_str, 'p')]:
            _fmt = align + '260' + fmt
            assert struct.calcsize(_fmt) == 260
            assert struct.pack(_fmt, b'hello-graal-python-'*20) == res

    # floats
    cases = [
        ('f', 1.12123123, 1.121231198310852),
        ('d', 1.12123123, 1.12123123),
        ('e', 1.12345678912345, 1.123046875),
        ('e', -145.12345678912345, -145.125),
    ]

    for align in alignment:
        for (fmt, val, res) in cases:
            _fmt = align + fmt
            _bytes = struct.pack(_fmt, val)
            _res = struct.unpack(_fmt, _bytes)[0]
            assert _res == res, "({}), {} != {}".format(_fmt, _res, res)


def test_pack_nan():
    import math
    assert struct.pack('<d', math.nan) == b'\x00\x00\x00\x00\x00\x00\xf8\x7f'


def test_pack_inf():
    assert struct.pack('<ff', float('inf'), float('-inf')) == b'\x00\x00\x80\x7f\x00\x00\x80\xff'


def test_alignment():
    assert struct.calcsize('3si') == struct.calcsize('ii')


def test_big_integer_multipack():
    value = -80000000000 << 100
    value >>= 100
    assert struct.pack('>Bq', 0x13, value) == b'\x13\xff\xff\xff\xed_\xa0\xe0\x00'


def test_pack_large_long():
    for fmt in ('l', 'q'):
        assert struct.pack(fmt, 0) == b'\x00' * struct.calcsize(fmt)
        assert struct.unpack(fmt, b'\x00' * struct.calcsize(fmt)) == (0,)
        assert struct.pack(fmt, -1) == b'\xff' * struct.calcsize(fmt)
        assert struct.unpack(fmt, b'\xff' * struct.calcsize(fmt)) == (-1,)

    for fmt in ('L', 'Q'):
        assert struct.pack(fmt, 0) == b'\x00' * struct.calcsize(fmt)
        assert struct.unpack(fmt, b'\x00' * struct.calcsize(fmt)) == (0,)
        assert struct.pack(fmt, 18446744073709551615) == b'\xff\xff\xff\xff\xff\xff\xff\xff'
        assert struct.unpack(fmt, b'\xff\xff\xff\xff\xff\xff\xff\xff') == (18446744073709551615,)


def test_pack_into():
    test_string = b'Reykjavik rocks, eow!'
    writable_buf = bytearray(b' '*100)
    fmt = '21s'
    s = struct.Struct(fmt)

    # Test without offset
    s.pack_into(writable_buf, 0, test_string)
    from_buf = writable_buf[:len(test_string)]
    assert bytes(from_buf) == test_string

    # Test with offset.
    s.pack_into(writable_buf, 10, test_string)
    from_buf = writable_buf[:len(test_string)+10]
    assert bytes(from_buf) == test_string[:10] + test_string

    # Go beyond boundaries.
    small_buf = bytearray(b' '*10)

    assert_raises((ValueError, struct.error), s.pack_into, small_buf, 0, test_string)
    assert_raises((ValueError, struct.error), s.pack_into, small_buf, 2, test_string)

    # Test bogus offset (issue 3694)
    sb = small_buf
    assert_raises((TypeError, struct.error), struct.pack_into, b'', sb, None)


def test_unpack_from():
    test_string = b'abcd01234'
    fmt = '4s'
    s = struct.Struct(fmt)
    for cls in (bytes, bytearray):
        data = cls(test_string)
        assert s.unpack_from(data) == (b'abcd',)
        assert s.unpack_from(data, 2) == (b'cd01',)
        assert s.unpack_from(data, 4) == (b'0123',)
        for i in range(6):
            assert s.unpack_from(data, i) == (data[i:i+4],)
        for i in range(6, len(test_string) + 1):
            assert_raises(struct.error, s.unpack_from, data, i)
    for cls in (bytes, bytearray):
        data = cls(test_string)
        assert struct.unpack_from(fmt, data) == (b'abcd',)
        assert struct.unpack_from(fmt, data, 2) == (b'cd01',)
        assert struct.unpack_from(fmt, data, 4) == (b'0123',)
        for i in range(6):
            assert struct.unpack_from(fmt, data, i) == (data[i:i+4],)
        for i in range(6, len(test_string) + 1):
            assert_raises(struct.error, struct.unpack_from, fmt, data, i)

    # keyword arguments
    assert s.unpack_from(buffer=test_string, offset=2) == (b'cd01',)

def test_iter_unpack():
    from collections import abc
    import operator

    s = struct.Struct('>ibcp')
    it = s.iter_unpack(b"")
    assert isinstance(it, abc.Iterator)
    assert isinstance(it, abc.Iterable)
    assert_raises(struct.error, s.iter_unpack, b"123456")

    s = struct.Struct('>')
    assert_raises(struct.error, s.iter_unpack, b"")

    s = struct.Struct('>IB')
    b = bytes(range(1, 16))
    it = s.iter_unpack(b)
    assert next(it) == (0x01020304, 5)
    assert next(it) == (0x06070809, 10)
    assert next(it) == (0x0b0c0d0e, 15)
    assert_raises(StopIteration, next, it)
    assert_raises(StopIteration, next, it)

    lh = operator.length_hint
    s = struct.Struct('>IB')
    b = bytes(range(1, 16))
    it = s.iter_unpack(b)
    assert lh(it) == 3
    next(it)
    assert lh(it) == 2
    next(it)
    assert lh(it) == 1
    next(it)
    assert lh(it) == 0
    assert_raises(StopIteration, next, it)
    assert lh(it) == 0


def test_pack_varargs():
    assert struct.Struct(">B").pack(3) == b'\x03'
    raised = False
    try:
        struct.Struct(">B").pack(3, kw=1)
    except TypeError:
        raised = True
    assert raised
    try:
        struct.Struct("iii").pack()
    except struct.error as e:
        assert "expected 3" in str(e), f"expected 3 not in {str(e)}"
