# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

class c_bool(bool):
    @staticmethod
    def size():
        return 1


class c_char(int):
    @staticmethod
    def size():
        return 1


class c_byte(int):
    @staticmethod
    def size():
        return 1


class c_ubyte(int):
    @staticmethod
    def size():
        return 1


class c_short(int):
    @staticmethod
    def size():
        return 2


class c_ushort(int):
    @staticmethod
    def size():
        return 2


class c_int(int):
    @staticmethod
    def size():
        return 4


class c_uint(int):
    @staticmethod
    def size():
        return 4


class c_long(int):
    @staticmethod
    def size():
        return 8


class c_ulong(int):
    @staticmethod
    def size():
        return 8


class c_longlong(int):
    @staticmethod
    def size():
        return 16


class c_ulonglong(int):
    @staticmethod
    def size():
        return 16


class c_size_t(int):
    @staticmethod
    def size():
        return 8


class c_ssize_t(int):
    @staticmethod
    def size():
        return 8


class c_float(float):
    @staticmethod
    def size():
        return 4


class c_double(float):
    @staticmethod
    def size():
        return 8


class c_char_p():
    def __init__(cls, value=None):
        return c_char_p_(value)

    @staticmethod
    def size():
        return 8


class c_void_p(int):
    @staticmethod
    def size():
        return 8


def sizeof(t):
    return t.size()
