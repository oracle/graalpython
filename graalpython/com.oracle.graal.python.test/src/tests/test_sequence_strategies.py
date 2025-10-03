# Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
import sys


if sys.implementation.name == "graalpy":
    if not hasattr(__graalpython__, 'get_storage_strategy'):
        raise SystemError("This test must run with --python.EnableDebuggingBuiltins=true")

    def check_strategy(container, expected):
        actual = __graalpython__.get_storage_strategy(container)
        assert actual == expected, f"Container '{container}', wrong storage strategy: '{actual}' != '{expected}'"
else:
    # For CPython, just to verify other test results
    def check_strategy(container, expected):
        pass


def test_appending_ints():
    l = list()
    for i in range(10):
        l.append(i)
    assert l[5] == 5
    check_strategy(l, "IntSequenceStorage")


def test_appending_doubles():
    l = list()
    for i in range(10):
        l.append(i * 0.1)
    assert l[5] == 0.5
    check_strategy(l, "DoubleSequenceStorage")


def test_generator_int():
    l = [x for x in range(10)]
    assert l[5] == 5
    check_strategy(l, "IntSequenceStorage")


def test_generator_double():
    l = [x * 0.1 for x in range(10)]
    assert l[5] == 0.5
    check_strategy(l, "DoubleSequenceStorage")


# GR-70364
# def test_generator_bool():
#     l = [x >= 5 for x in range(10)]
#     assert l[5] == True
#     check_strategy(l, "BoolSequenceStorage")


def test_literal_int():
    l = [1,2,3,4,5]
    assert l[4] == 5
    check_strategy(l, "IntSequenceStorage")


def test_literal_double():
    l = [1.1,2.2,3.3,4.4,0.5]
    assert l[4] == 0.5
    check_strategy(l, "DoubleSequenceStorage")


def test_literal_mixed():
    l = [1,2,3,4,0.5]
    assert l[4] == 0.5
    check_strategy(l, "ObjectSequenceStorage")
