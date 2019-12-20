# Copyright (c) 2018, 2019, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.

import sys


def test_list():
    l = [0, 1, 2, 3, 4]
    assert l[0:0] == []
    assert l[1:2] == [1]
    assert l[-2:-1] == [3]
    assert l[-100:100] == [0, 1, 2, 3, 4]
    assert l[100:-100] == []
    assert l[:] == [0, 1, 2, 3, 4]
    assert l[1:None] == [ 1, 2, 3, 4]
    assert l[None:3] == [0, 1, 2]

    # extended slice
    assert l[::] == [0, 1, 2, 3, 4]
    assert l[::2] == [0, 2, 4]
    assert l[1::2] == [1, 3]
    assert l[::-1] == [4, 3, 2, 1, 0]
    assert l[::-2] == [4, 2, 0]
    assert l[3::-2] == [3, 1]
    assert l[3:3:-2] == []
    assert l[3:2:-2] == [3]
    assert l[3:1:-2] == [3]
    assert l[3:0:-2] == [3, 1]
    assert l[::-100] == [4]
    assert l[100:-100:] == []
    assert l[-100:100:] == [0, 1, 2, 3, 4]
    assert l[100:-100:-1] == [4, 3, 2, 1, 0]
    assert l[-100:100:-1] == []
    assert l[-100:100:2] == [0, 2, 4]


def test_tuple():
    t = (0, 1, 2, 3, 4)
    assert t[0:0] == tuple()  # []
    assert t[1:2] == (1,)  # [1]
    assert t[-2:-1] == (3,)  # [3]
    assert t[-100:100] == (0, 1, 2, 3, 4)  # [0, 1, 2, 3, 4]
    assert t[100:-100] == tuple()  # []
    assert t[:] == (0, 1, 2, 3, 4)  # [0, 1, 2, 3, 4]
    assert t[1:None] == (1, 2, 3, 4)  # [1, 2, 3, 4]
    assert t[None:3] == (0, 1, 2)  # [0, 1, 2]

    # extended slice
    assert t[::] == (0, 1, 2, 3, 4)  # [0, 1, 2, 3, 4]
    assert t[::2] == (0, 2, 4)  # [0, 2, 4]
    assert t[1::2] == (1, 3)  # [1, 3]
    assert t[::-1] == (4, 3, 2, 1, 0)  # [4, 3, 2, 1, 0]
    assert t[::-2] == (4, 2, 0)  # [4, 2, 0]
    assert t[3::-2] == (3, 1)  # [3, 1]
    assert t[3:3:-2] == tuple()  # []
    assert t[3:2:-2] == (3,)  # [3]
    assert t[3:1:-2] == (3,)  # [3]
    assert t[3:0:-2] == (3, 1)  # [3, 1]
    assert t[::-100] == (4,)  # [4]
    assert t[100:-100:] == tuple()  # []
    assert t[-100:100:] == (0, 1, 2, 3, 4)  # [0, 1, 2, 3, 4]
    assert t[100:-100:-1] == (4, 3, 2, 1, 0)  # [4, 3, 2, 1, 0]
    assert t[-100:100:-1] == tuple()  # []
    assert t[-100:100:2] == (0, 2, 4)  # [0, 2, 4]


def test_string():
    s = "01234"
    assert s[0:0] == ""
    assert s[1:2] == "1"
    assert s[-2:-1] == "3"
    assert s[-100:100] == "01234"
    assert s[100:-100] == ""
    assert s[:] == "01234"
    assert s[1:None] == "1234"
    assert s[None:3] == "012"

    # extended slice
    assert s[::] == "01234"
    assert s[::2] == "024"
    assert s[1::2] == "13"
    assert s[::-1] == "43210"
    assert s[::-2] == "420"
    assert s[3::-2] == "31"
    assert s[3:3:-2] == ""
    assert s[3:2:-2] == "3"
    assert s[3:1:-2] == "3"
    assert s[3:0:-2] == "31"
    assert s[::-100] == "4"
    assert s[100:-100:] == ""
    assert s[-100:100:] == "01234"
    assert s[100:-100:-1] == "43210"
    assert s[-100:100:-1] == ""
    assert s[-100:100:2] == "024"


def test_range_step1():
    t = range(5)
    assert t[0:0] == range(0)
    assert t[1:2] == range(1, 2)
    assert t[-2:-1] == range(3, 4)
    assert t[-100:100] == range(0, 5)
    assert t[100:-100] == range(5, 0)
    assert t[:] == range(0, 5)
    assert t[1:None] == range(1, 5)
    assert t[None:3] == range(0, 3)

    # extended slice
    assert t[::] == range(0, 5)
    assert t[::2] == range(0, 5, 2)
    assert t[1::2] == range(1, 4, 2)
    assert t[::-1] == range(4, -1, -1)
    assert t[::-2] == range(4, -1, -2)

    if sys.version_info.minor >= 7:
        assert t[3::-2] == range(3, 0, -2)
    else:
        assert t[3::-2] == range(3, -1, -2)
    assert t[3:3:-2] == range(0)
    assert t[3:2:-2] == range(3, 2, -2)
    assert t[3:1:-2] == range(3, 1, -2)
    assert t[3:0:-2] == range(3, 0, -2)
    assert t[::-100] == range(4, 5, 1)
    assert t[100:-100:] == range(0)
    assert t[-100:100:] == range(5)
    assert t[100:-100:-1] == range(4, -1, -1)
    assert t[-100:100:-1] == range(0)
    assert t[-100:100:2] == range(0, 5, 2)


def test_range_step2():
    t = range(5, 15, 2)
    assert t[0:0] == range(5, 5, 2)
    assert t[1:2] == range(7, 9, 2)
    assert t[-2:-1] == range(11, 13, 2)
    assert t[-100:100] == range(5, 15, 2)
    assert t[100:-100] == range(15, 5, 2)
    assert t[:] == range(5, 15, 2)
    assert t[1:None] == range(7, 15, 2)
    assert t[None:3] == range(5, 11, 2)

    # extended slice
    assert t[::] == range(5, 15, 2)
    assert t[::2] == range(5, 15, 4)
    assert t[1::2] == range(7, 15, 4)
    assert t[::-1] == range(13, 3, -2)
    assert t[::-2] == range(13, 3, -4)

    assert t[3::-2] == range(11, 3, -4)
    assert t[3:3:-2] == range(11, 11, -4)
    assert t[3:2:-2] == range(11, 9, -4)
    assert t[3:1:-2] == range(11, 7, -4)
    assert t[3:0:-2] == range(11, 5, -4)
    assert t[::-100] == range(13, 3, -200)
    assert t[100:-100:] == range(15, 5, 2)
    assert t[-100:100:] == range(5, 15, 2)
    assert t[100:-100:-1] == range(13, 3, -2)
    assert t[-100:100:-1] == range(3, 13, -2)
    assert t[-100:100:2] == range(5, 15, 4)


def test_correct_error():
    class X():
        def __index__(self):
            return "42"

    try:
        [1][:X()]
    except TypeError as e:
        assert "__index__ returned non-int" in str(e)

    try:
        [1][:"42"]
    except TypeError as e:
        assert "slice indices must be integers" in str(e)
