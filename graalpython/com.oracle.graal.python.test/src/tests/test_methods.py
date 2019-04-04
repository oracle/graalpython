# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

class X():
    def foo(self):
        return self


def test_method():
    x = X()
    assert x.foo() is x


def test_unbound_method():
    x = X()
    assert X.foo(x) is x


def test_call_method():
    x = X()
    assert x.foo.__call__() is x


def test_call_unbound_method():
    x = X()
    assert X.foo.__call__(x) is x


def test_call_builtin_method():
    x = {1: 2}
    assert x.__getitem__.__call__(1) == 2


def test_call_builtin_unbound_method():
    x = {1: 2}
    assert dict.__getitem__.__call__(x, 1) == 2


def test_make_method():
    method_type = type(X().foo)

    class A():
        def __init__(self, *args):
            pass

        def __call__(self, x, y):
            assert isinstance(x, str)
            assert isinstance(self, A)
            return "A" + str(x) + str(y)

    method1 = method_type(A, A)
    method2 = method_type(A(), " is ")

    assert isinstance(method1(), A)
    assert method2(1) == "A is 1", method2(1)
