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

"""Allocate two-field records into a reused output buffer. Setup is untimed."""

_output = None
_xs = None
_ys = None


class Record:
    def __init__(self, x, y):
        self.x = x
        self.y = y


def __setup__(size=4096):
    global _output, _xs, _ys
    if size <= 0:
        raise ValueError("size must be positive")
    _output = [None] * size
    _xs = [0] * size
    _ys = [0] * size
    seed = 42
    for i in range(size):
        seed = (seed * 25173 + 13849) & 65535
        x = seed & 32767
        seed = (seed * 25173 + 13849) & 65535
        _xs[i] = x
        _ys[i] = seed & 32767


def __benchmark__(size=4096):
    output = _output
    xs = _xs
    ys = _ys
    for i in range(size):
        output[i] = Record(xs[i], ys[i])


def __teardown__():
    global _output, _xs, _ys
    # Inspect retained records outside timing.
    for i in range(len(_output)):
        if _output[i] is None or _output[i].x != _xs[i] or _output[i].y != _ys[i]:
            raise AssertionError("Incorrect output record at " + str(i))
    _output = None
    _xs = None
    _ys = None
