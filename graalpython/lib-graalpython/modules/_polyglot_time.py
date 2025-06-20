# Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import time
import polyglot


def _struct_time_tz(st: time.struct_time):
    if st.tm_gmtoff is not None:
        return st.tm_gmtoff
    return st.tm_zone


polyglot.register_interop_behavior(time.struct_time,
                                   is_date=True, as_date=lambda t: (t.tm_year, t.tm_mon, t.tm_mday),
                                   is_time=True, as_time=lambda t: (t.tm_hour, t.tm_min, t.tm_sec, 0),
                                   is_time_zone=lambda t: t.tm_zone is not None or t.tm_gmtoff is not None,
                                   as_time_zone=_struct_time_tz)

# example extending time.struct_time using the decorator wrapper
#
# @polyglot.interop_behavior(time.struct_time)
# class StructTimeInteropBehavior:
#     @staticmethod
#     def is_date(t):
#         return True
#
#     @staticmethod
#     def as_date(t):
#         return t.tm_year, t.tm_mon, t.tm_mday
#
#     @staticmethod
#     def is_time(t):
#         return True
#
#     @staticmethod
#     def as_time(t):
#         return t.tm_hour, t.tm_min, t.tm_sec, 0
#
#     @staticmethod
#     def is_time_zone(t):
#         return t.tm_zone is not None or t.tm_gmtoff is not None
#
#     @staticmethod
#     def as_time_zone(t):
#         if t.tm_gmtoff is not None:
#             return t.tm_gmtoff
#         return t.tm_zone
