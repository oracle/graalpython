# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import sys
import struct

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
#            rev = struct.unpack(xfmt, res)[0]
#            if rev != arg:
#                assert asy


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
            assert vals == struct.unpack(_fmt, result)
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
        # ('f', 1.12123123, 1.121231198310852),
        # ('d', 1.12123123, 1.12123123),
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
