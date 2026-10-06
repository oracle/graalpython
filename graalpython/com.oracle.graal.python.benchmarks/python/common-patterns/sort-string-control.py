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

"""Copy a prepared unsorted ASCII string array/list.

4096 deterministic inputs, appended once with LCG seed 42. Each timed
invocation copies the original input; setup and final validation are untimed.
The copy-only control includes allocation and retention of the copied list.
"""

_input = None
_output = None
_expected = None


def __setup__(size=4096):
    global _input, _output, _expected
    if size <= 0:
        raise ValueError("size must be positive")
    _input = []
    _output = None
    seed = 42
    for i in range(size):
        seed = (seed * 25173 + 13849) & 65535
        length = 4 + ((seed >> 8) & 15)
        text = ""
        for k in range(length):
            seed = (seed * 25173 + 13849) & 65535
            text += chr(97 + ((seed >> 8) % 26))
        _input.append(text)
    # Independent insertion-sort oracle; do not call the measured sort here.
    _expected = _input.copy()
    for i in range(1, size):
        value = _expected[i]
        k = i
        while k > 0 and _expected[k - 1] > value:
            _expected[k] = _expected[k - 1]
            k -= 1
        _expected[k] = value


def __benchmark__(size=4096):
    global _output
    _output = _input.copy()


def __teardown__():
    global _input, _output, _expected
    if _output is None or _output is _input or len(_output) != len(_input):
        raise AssertionError("Expected a separate result list of the input size")
    if _output != _input:
        raise AssertionError("Incorrect output")
    _input = None
    _output = None
    _expected = None
