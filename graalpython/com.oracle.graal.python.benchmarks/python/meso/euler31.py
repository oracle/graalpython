#!/usr/bin/env python
# Copyright 2008-2010 Isaac Gouy
# Copyright (c) 2013, 2014, Regents of the University of California
# Copyright (c) 2017, 2020, Oracle and/or its affiliates.
# All rights reserved.
#
# Revised BSD license
#
# This is a specific instance of the Open Source Initiative (OSI) BSD license
# template http://www.opensource.org/licenses/bsd-license.php
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
#   Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
#
#   Redistributions in binary form must reproduce the above copyright notice,
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
#
#   Neither the name of "The Computer Language Benchmarks Game" nor the name of
#   "The Computer Language Shootout Benchmarks" nor the name "nanobench" nor the
#   name "bencher" nor the names of its contributors may be used to endorse or
#   promote products derived from this software without specific prior written
#   permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

#runas solve()
#unittest.skip recursive generator
#pythran export solve()
# 01/08/14 modified for benchmarking by Wei Zhang
COINS = [1, 2, 5, 10, 20, 50, 100, 200]


# test
def _sum(iterable):
    sum = None
    for i in iterable:
        if sum is None:
            sum = i
        else:
            sum += i

    return sum


def balance(pattern): 
    return _sum(COINS[x]*pattern[x] for x in range(0, len(pattern)))


def gen(pattern, coinnum, num):
    coin = COINS[coinnum]
    for p in range(0, num//coin + 1):
        newpat = pattern[:coinnum] + (p,)
        bal = balance(newpat)

        if bal > num: 
            return
        elif bal == num: 
            yield newpat
        elif coinnum < len(COINS)-1:
            for pat in gen(newpat, coinnum+1, num):
                yield pat


def solve(total):
    '''
    In England the currency is made up of pound, P, and pence, p, and there are eight coins in general circulation:

    1p, 2p, 5p, 10p, 20p, 50p, P1 (100p) and P2 (200p).
    It is possible to make P2 in the following way:

    1 P1 + 1 50p + 2 20p + 1 5p + 1 2p + 3 1p
    How many different ways can P2 be made using any number of coins?
    '''
    return _sum(1 for pat in gen((), 0, total))


def measure(num):
    result = solve(num)
    print('total number of different ways: ', result)
    

def __benchmark__(num=200):
    measure(num)
