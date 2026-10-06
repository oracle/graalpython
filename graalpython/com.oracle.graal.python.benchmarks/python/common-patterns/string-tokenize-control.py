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

"""Copy prepared string references into a reused output buffer.

4096 strings, each with 4..11 words from a fixed vocabulary; deterministic
LCG seed 42. Some strings have leading, repeated or trailing spaces.
Setup and final full-output validation are untimed. Inputs are reused.
The control measures reference copying; its cost is not exactly subtractable.
"""

_texts = None
_output = None
_expected = None


def __setup__(size=4096):
    global _texts, _output, _expected
    if size <= 0:
        raise ValueError("size must be positive")
    words = ['a', 'to', 'red', 'blue', 'apple', 'tokens', 'example', 'compiler', 'benchmark', 'allocation', 'performance', 'deterministic', 'x', 'go', 'word', 'string']
    _texts = [None] * size
    _output = [None] * size
    _expected = [None] * size
    seed = 42
    for i in range(size):
        seed = (seed * 25173 + 13849) & 65535
        count = 4 + ((seed >> 8) & 7)
        tokens = []
        if i % 8 == 0:
            tokens.append("")
        for j in range(count):
            seed = (seed * 25173 + 13849) & 65535
            tokens.append(words[(seed >> 8) & 15])
            if i % 4 == 0 and j == 1:
                tokens.append("")
        if i % 16 == 0:
            tokens.append("")
        _texts[i] = " ".join(tokens)
        _expected[i] = tokens


def __benchmark__(size=4096):
    texts = _texts
    output = _output
    for i in range(size):
        output[i] = texts[i]


def __teardown__():
    global _texts, _output, _expected
    for i in range(len(_output)):
        if _output[i] is not _texts[i]:
            raise AssertionError("Incorrect copied string at " + str(i))
    _texts = None
    _output = None
    _expected = None
