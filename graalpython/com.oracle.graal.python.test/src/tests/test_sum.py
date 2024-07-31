# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

from .util import assert_raises

def test_type_int():
    # does not fit into primitive 'long'
    i = 0xfffffffffffffffffffffffffffffffffff
    assert type(i) == int, "expected type 'int' but was '%s'" % type(i)
    l = [i, i+1]
    t = type(sum(l))
    t = type(sum(l))
    assert t == int, "expected result type 'int' but was '%s'" % t


def test_reexecution():
    assert sum([1,2,2.1,3,9.5].__iter__()) == 17.6
    assert sum([1,2,2.1,3,9.5]) == 17.6
    assert sum([1,2,2,3,9].__iter__()) == 17
    assert sum([1,2,2,3,9]) == 17
    assert sum([2.1,3,9.5,1].__iter__()) == 15.6
    assert sum([2.1,3,9.5,1]) == 15.6

class SumTestClass:
    def __init__(self):
        self.counter = 0

    def __iter__(self):
        return(self)

    def __next__(self):
        self.counter += 1
        if self.counter == 10:
            raise StopIteration
        return(self.counter)

def test_iterator():
    assert sum(SumTestClass()) == 45

def test_basics():
    assert sum([[1, 2], [3, 4]], []) == [1, 2, 3, 4]
    assert_raises(TypeError, sum, [1,2,3], None)
