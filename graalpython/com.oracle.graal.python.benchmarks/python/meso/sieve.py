# Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import math


class Natural:
    n = 2

    def next(self):
        r = self.n
        self.n = self.n + 1
        return r


class Filter:
    def __init__(self, n):
        self.number = n
        self.next = None
        self.last = self

    def acceptAndAdd(self, n):
        filter = self
        sqrt = math.sqrt(n)
        while True:
            if n % filter.number == 0:
                return False
            if filter.number > sqrt:
                break
            filter = filter.next

        newFilter = Filter(n)
        self.last.next = newFilter
        self.last = newFilter
        return True


class Primes:
    def __init__(self, natural):
        self.natural = natural
        self.filter = None

    def next(self):
        while True:
            n = self.natural.next()
            if (self.filter == None):
                self.filter = Filter(n)
                return n
            if (self.filter.acceptAndAdd(n)):
                return n


def measure(prntCnt, upto):
    primes = Primes(Natural())
    cnt = 0
    res = -1
    while cnt < upto:
        res = primes.next()
        cnt = cnt + 1
        if (cnt % prntCnt == 0):
            print("Computed %s primes. Last one is %s" % (cnt, res))
            prntCnt = prntCnt * 2


def __benchmark__(num=100000):
    measure(num, num)
