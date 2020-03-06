#!/usr/bin/env python
# Copyright 2008-2010 Isaac Gouy
# Copyright (c) 2013, 2014, Regents of the University of California
# Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

# The Computer Language Benchmarks Game
# http://shootout.alioth.debian.org/
#
# modified by Ian Osgood
# modified again by Heinrich Acker
# modified by Justin Peel
# modified by Mariano Chouza
# 2to3

import bisect, array
bb = bisect.bisect

alu = (
   'GGCCGGGCGCGGTGGCTCACGCCTGTAATCCCAGCACTTTGG'
   'GAGGCCGAGGCGGGCGGATCACCTGAGGTCAGGAGTTCGAGA'
   'CCAGCCTGGCCAACATGGTGAAACCCCGTCTCTACTAAAAAT'
   'ACAAAAATTAGCCGGGCGTGGTGGCGCGCGCCTGTAATCCCA'
   'GCTACTCGGGAGGCTGAGGCAGGAGAATCGCTTGAACCCGGG'
   'AGGCGGAGGTTGCAGTGAGCCGAGATCGCGCCACTGCACTCC'
   'AGCCTGGGCGACAGAGCGAGACTCCGTCTCAAAAA')

iub = list(zip('acgtBDHKMNRSVWY', [0.27, 0.12, 0.12, 0.27] + [0.02]*11))

homosapiens = [
    ('a', 0.3029549426680),
    ('c', 0.1979883004921),
    ('g', 0.1975473066391),
    ('t', 0.3015094502008),
]

IM = 139968
INITIAL_STATE = 42


def makeCumulative(table):
    P = []
    C = []
    prob = 0.
    for char, p in table:
        prob += p
        P += [prob]
        C += [char]
    return (P, C)

randomGenState = INITIAL_STATE
randomLUT = None


def makeRandomLUT():
    global randomLUT
    ia = 3877; ic = 29573
    randomLUT = [(s * ia + ic) % IM for s in range(IM)]


def makeLookupTable(table):
    # bb = bisect.bisect
    probs, chars = makeCumulative(table)
    imf = float(IM)
    return [chars[bb(probs, i / imf)] for i in range(IM)]


def repeatFasta(src, n):
    width = 60
    r = len(src)
    s = src + src + src[:n % r]
    for j in range(n // width):
        i = j*width % r
        #print(s[i:i+width])
    if n % width:
        #print(s[-(n % width):])
        s[-(n % width):]


def randomFasta(table, n):
    global randomLUT, randomGenState
    width = 60
    rgs = randomGenState
    rlut = randomLUT
    
    lut = makeLookupTable(table)
    line_buffer = []
    #la = line_buffer.append
    
    for i in range(n // width):
        for i in range(width):
            rgs = rlut[rgs]
            #la(lut[rgs])
            line_buffer.append(lut[rgs])
        #print(''.join(line_buffer))
        line_buffer[:] = []
    if n % width:
        for i in range(n % width):
            rgs = rlut[rgs]
            #la(lut[rgs])
            line_buffer.append(lut[rgs])
        #print(''.join(line_buffer))
    
    randomGenState = rgs


def main(n):
    #n = int(sys.argv[1])

    makeRandomLUT()

    #print('>ONE Homo sapiens alu')
    repeatFasta(alu, n*2)

    #print('>TWO IUB ambiguity codes')
    randomFasta(iub, n*3)

    #print('>THREE Homo sapiens frequency')
    randomFasta(homosapiens, n*5)


def measure(num):
    main(num)
    

def reset():
    global randomGenState, randomLUT
    randomGenState = INITIAL_STATE
    randomLUT = None    


def __benchmark__(num=25000000):
    reset()
    measure(num)
