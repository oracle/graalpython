# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

from _descriptor import SimpleNamespace

@__graalpython__.builtin
def strptime(data_string, format="%a %b %d %H:%M:%S %Y"):
    from _strptime import _strptime_time
    return _strptime_time(data_string, format)

@__graalpython__.builtin
def get_clock_info(name):
    if not isinstance(name, str):
        raise(TypeError("argument 1 must be str, not int"))

    # cpython gives resolution 1e-9 in some cases, 
    # but jdks System.nanoTime() does not guarantee that
    resolution = 1e-6
    if name == 'monotonic':
        adjustable = False
        implementation = "monotonic"
        monotonic = True
    elif name == 'perf_counter':
        adjustable = False
        implementation = "perf_counter"
        monotonic = True
    elif name == 'process_time':    
        adjustable = False
        implementation = "process_time"
        monotonic = True
    elif name == 'thread_time':
        adjustable = False
        implementation = "thread_time"
        monotonic = True
    elif name == 'time':
        adjustable = True
        implementation = "time"
        monotonic = False
    else:
        raise(ValueError("unknown clock"))

    result = SimpleNamespace(
        adjustable = adjustable,
        implementation=implementation,
        monotonic=monotonic,
        resolution=resolution
    )
    return result