# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

def test_small_int_id_is_constant():
    assert id(12) == id(12)
    x = 128
    y = 128
    assert id(x) == id(y) == id(128)


def test_object_id_is_constant():
    x = object()
    xid = id(x)
    assert id(x) == xid


def test_double_id_is_constant():
    assert id(12.2) == id(12.2)
    x = 128.12
    y = 128.12
    assert id(x) == id(y) == id(128.12)


def test_id_is_constant_even_when_object_changes():
    l = []
    assert id(l) == id(l)
    lid = id(l)
    l.append(12)
    assert id(l) == lid

def test_identity():
    assert True is True
    assert memoryview(b"").readonly is True # compare a PInt bool (from C) to a boolean

    assert 12 is 12
    assert memoryview(b"123").nbytes == 3 # compare PInt (from C) to an int
    class I(int): pass
    assert I(12) is not 12
    assert 12.0 is 12.0

    nan = float('nan')
    assert nan is nan
