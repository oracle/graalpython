# Copyright (c) 2018, 2020, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# sum a generator expression

def test_sum():
    genexp = (x*2 for x in range(5))

    def _sum(iterable):
        sum = 0
        for i in iterable:
            sum += i
        return sum

    assert _sum(genexp) == 20
    assert _sum(genexp) == 0


def test_sum2():
    genexp = (x*2 for x in range(5))

    sum = 0
    for i in genexp:
        sum += i
    assert sum == 20

    sum = 0
    for i in genexp:
        sum += i
    assert sum == 0


def test_genexp():
    genexp = (x*2 for x in range(5))

    genexp.__next__()
    genexp.__next__()
    genexp.__next__()
    genexp.__next__()
    assert genexp.__next__() == 8
    

def test_list_comprehension():
    def make_list(size):
        return [i for i in range(size)]

    ll = make_list(100000)
    assert ll[-1] == 99999

    ll = [i for i in range(0, 1, 2)]
    assert ll == [0], "expected '[0]' but was '%r'" % ll
