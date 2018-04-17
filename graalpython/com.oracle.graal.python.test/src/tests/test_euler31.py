# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (c) 2013-2016, Regents of the University of California
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

#runas solve()
#unittest.skip recursive generator
#pythran export solve()
def solve():
    '''
    In England the currency is made up of pound, P, and pence, p, and there are eight coins in general circulation:

    1p, 2p, 5p, 10p, 20p, 50p, P1 (100p) and P2 (200p).
    It is possible to make P2 in the following way:

    1 P1 + 1 50p + 2 20p + 1 5p + 1 2p + 3 1p
    How many different ways can P2 be made using any number of coins?
    '''

    coins = [1, 2, 5, 10, 20, 50, 100, 200]

    def balance(pattern):
        return sum(coins[x]*pattern[x] for x in range(0, len(pattern)))

    def gen(pattern, coinnum, num):
        coin = coins[coinnum]
        for p in range(0, num//coin + 1):
            newpat = pattern[:coinnum] + (p,)
            bal = balance(newpat)
            if bal > num:
                return
            elif bal == num: yield newpat
            elif coinnum < len(coins)-1:
                for pat in gen(newpat, coinnum+1, num):
                    yield pat

    return sum(1 for pat in gen((), 0, 20))

def test_euler():
    assert solve() == 41
