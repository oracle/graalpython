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

# This is PADS, a library of Python Algorithms and Data Structures
# implemented by David Eppstein of the University of California, Irvine.
#
# The current version of PADS may be found at
# <http://www.ics.uci.edu/~eppstein/PADS/>, as individual files or as a
# git repository that may be copied by the command line
#
# 02/24/14 Modified by Wei Zhang


def updateDict(dict, primaryKey, secondKey, val):
        if primaryKey in dict:
                dict[primaryKey][secondKey] = val
        else:
                dict[primaryKey] = {secondKey : val}


def FactoredIntegers():
    """
    Generate pairs n,F where F is the prime factorization of n.
    F is represented as a dictionary in which each prime factor of n
    is a key and the exponent of that prime is the corresponding value.
    """
    yield 1,{}
    i = 2
    factorization = {}
    while True:
        if i not in factorization:  # prime
            F = {i:1}
            yield i,F
            factorization[2*i] = F
        elif len(factorization[i]) == 1:    # prime power
            p, x = next(iter(factorization[i].items()))

            F = {p:x+1}
            yield i,F
            factorization[2*i] = F
            updateDict(factorization, i+p**x, p, x)
            del factorization[i]
        else:
            yield i,factorization[i]
            for p,x in factorization[i].items():
                q = p**x
                iq = i+q
                if iq in factorization and p in factorization[iq]:
                    iq += p**x  # skip higher power of p
                updateDict(factorization, iq, p, x)

            del factorization[i]
        i += 1


def isPracticalFactorization(f):
    """Test whether f is the factorization of a practical number."""
    f = list(f.items())
    f.sort()
    sigma = 1
    for p,x in f:
        if sigma < p - 1:
            return False
        sigma *= (p**(x+1)-1)//(p-1)
    return True


def PracticalNumbers():
    """Generate the sequence of practical (or panarithmic) numbers."""
    for x,f in FactoredIntegers():
        if isPracticalFactorization(f):
            yield x


def main(n):
    """Test that the first few practical nos are generated correctly."""
    # G = PracticalNumbers()
    # for p in [1,2,4,6,8,12,16,18,20,24,28,30,32,36]:
        # self.assertEqual(p,G.next())
    # nums = []
    # for i in range(10):
    #   nums.append(next(G))
    # print(nums)

    nums = []
    for num in PracticalNumbers():
        nums.append(num)
        if len(nums) == n:
            break;

    return nums[-1]


def measure(num):
    result = main(num)
    print(result)
    

def __benchmark__(num=100000):
    measure(num)
