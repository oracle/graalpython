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


def make_struct_time():
    from _descriptor import make_named_tuple_class
    fields = ["tm_year", "tm_mon", "tm_mday", "tm_hour", "tm_min", "tm_sec", "tm_wday", "tm_yday", "tm_isdst", "tm_zone", "tm_gmtoff"]
    struct_time_type = make_named_tuple_class("struct_time", fields)

    class struct_time(struct_time_type):

        def __new__(cls, iterable):
            count = len(iterable)
            if (count < 9):
                raise TypeError("time.struct_time() takes an at least 9-sequence (%d-sequence given)" % count)
            if (count > 11):
                raise TypeError("time.struct_time() takes an at most 11-sequence (%d-sequence given)" % count)
            if count == 11:
                return tuple.__new__(cls, iterable)
            if count == 10:
                return tuple.__new__(cls, iterable + (None, ))
            if count == 9:
                return tuple.__new__(cls, iterable + (None,  None)) 

        def __repr__(self):
            text = "{}(".format(self.__class__.__name__)
            n = len(self)
            for i in range(n):
                if self[i] != None:
                    if i > 0 :
                        text = text + ", "
                    text = text + "{}={}".format(fields[i], str(self[i]))
            text = text + ')'
            return text
    return struct_time


struct_time = make_struct_time()
del make_struct_time

@__builtin__
def gmtime(seconds):
    return struct_time(__truffle_gmtime_tuple__(seconds))


@__builtin__
def localtime(seconds):
    return struct_time(__truffle_localtime_tuple__(seconds))
