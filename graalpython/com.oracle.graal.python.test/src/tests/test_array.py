# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

from array import array


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_argument_validation():
    a = array('d', [1.1, 3.5])
    assert_raises(TypeError, a.__reduce_ex__, None)
    assert_raises(TypeError, a.fromfile, 'bogus', None)
    assert_raises(TypeError, a.insert, None, 42.42)
    assert_raises(TypeError, a.__imul__, None)
    assert_raises(TypeError, a.__mul__, None)


def test_create():
    a = array('b', b'x' * 10)
    assert str(a) == "array('b', [120, 120, 120, 120, 120, 120, 120, 120, 120, 120])"


def test_wrong_create():
    raised = False
    try:
        a = array([1, 2, 3])
    except TypeError:
        raised = True
    assert raised


def test_add():
    a0 = array("b", b"hello")
    a1 = array("b", b"world")
    assert a0 + a1 == array("b", b"helloworld")

    a0 = array("b", b"hello")
    a1 = array("l", b"abcdabcd")
    try:
        res = a0 + a1
    except TypeError:
        assert True
    else:
        assert False


def test_add_int_to_long_storage():
    x = [2147483648, 1]
    x[0] = 42  # should not raise
    assert x[0] == 42


def test_add_int_to_long_array():
    y = array('l', [1, 2])
    y[0] = 42  # should not raise
    assert y[0] == 42


def test_array_native_storage():
    a = array('l', [1, 2, 3])
    if sys.implementation.name == 'graalpy':
        assert hasattr(__graalpython__, 'storage_to_native'), "Needs to be run with --python.EnableDebuggingBuiltins"
        __graalpython__.storage_to_native(a)
    assert a[1] == 2
    a[1] = 3
    assert a == array('l', [1, 3, 3])
    assert a[1:] == array('l', [3, 3])
    del a[2:]
    a.insert(1, -1)
    assert a == array('l', [1, -1, 3])
