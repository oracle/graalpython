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

"""Successful string-key lookup in a prebuilt Map/dict, consuming each integer.
Keys and bounded LCG values are prepared outside timing (4096 entries).
Stored and query keys are constructed separately with identical ASCII text;
string identity and hash caching are runtime-dependent. Queries are reused.
map-lookup-control measures key loads and consumption without map access.
Its string blackhole differs from this integer blackhole, so subtraction is
not an exact lookup cost. This benchmark also controls map-update.
"""

_keys = None
_values = None
_mapping = None


def __setup__(size=4096):
    global _keys, _values, _mapping
    if size <= 0:
        raise ValueError("size must be positive")
    _keys = [None] * size
    _values = [0] * size
    _mapping = {}
    seed = 42
    for i in range(size):
        _keys[i] = "key-" + str(i)
        seed = (seed * 25173 + 13849) & 65535
        _values[i] = seed & 32767
        _mapping["key-" + str(i)] = _values[i]


def __benchmark__(size=4096):
    keys = _keys
    mapping = _mapping
    consume = blackhole
    for i in range(size):
        consume(mapping[keys[i]])


def __teardown__():
    global _keys, _values, _mapping
    if _mapping is None or len(_mapping) != len(_keys):
        raise AssertionError("Incorrect map size")
    for i in range(len(_keys)):
        expected = _values[i]
        if _mapping[_keys[i]] != expected:
            raise AssertionError("Incorrect map value at " + str(i))
    _keys = None
    _values = None
    _mapping = None
