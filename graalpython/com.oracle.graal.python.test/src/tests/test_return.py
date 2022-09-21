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

def test_return_val():
    def method(v):
        return v

    assert method(10) == 10
    assert method("value") == "value"


def test_return_noval():
    def method(v):
        return

    assert method(10) is None
    assert method("value") is None

def test_return_in_finally():
    def override_explicit():
        try:
            return 0
        finally:
            return 42

    def override_implicit():
        try:
            pass
        finally:
            return 'correct'

    assert override_explicit() == 42
    assert override_implicit() == 'correct'

def test_return_in_try():
    def basic(arg):
        try:
            if arg:
                raise ValueError()
            return 1 # can be 'terminating'
        except ValueError as e:
            return 2 # can be 'terminating'

    assert basic(True) == 2
    assert basic(False) == 1

    def with_orelse(arg):
        try:
            if arg:
                return 'try'
        except:
            pass
        else:
            return 'else'

    assert with_orelse(True) == 'try'
    assert with_orelse(False) == 'else'


def test_return_in_try_finally():
    def foo(arg):
        try:
            if arg:
                raise ValueError()
            return 'try'
        except ValueError as e:
            return 'except'
        finally:
            return 'finally'

    assert foo(True) == 'finally'
    assert foo(False) == 'finally'
