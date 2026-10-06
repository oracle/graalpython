# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

"""Call 4 distinct targets with 3 positional arguments over 4096 elements.
Separate definitions have identical arithmetic bodies, using every argument.
Arguments vary with the index; normal inlining remains enabled.
Shared inline-arithmetic control: function-dispatch-args3-control.
"""

_functions = None


def m0(a0, a1, a2):
    return a0 * 1 + a1 * 2 + a2 * 3


def m1(a0, a1, a2):
    return a0 * 1 + a1 * 2 + a2 * 3


def m2(a0, a1, a2):
    return a0 * 1 + a1 * 2 + a2 * 3


def m3(a0, a1, a2):
    return a0 * 1 + a1 * 2 + a2 * 3


def __setup__(size=4096):
    global _functions
    if size <= 0:
        raise ValueError("size must be positive")
    targets = [m0, m1, m2, m3]
    _functions = [None] * size
    for i in range(size):
        _functions[i] = targets[i % 4]


def __benchmark__(size=4096):
    functions = _functions
    consume = blackhole
    for i in range(size):
        target = functions[i]
        a0 = i + 0
        a1 = i + 1
        a2 = i + 2
        result = target(a0, a1, a2)
        consume(result)


def __teardown__():
    global _functions
    _functions = None
