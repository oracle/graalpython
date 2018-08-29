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
# submitted by Ian Osgood
# modified by Sokolov Yura
# modified by bearophile
# modified by Justin Peel

from collections import defaultdict


def gen_freq(seq, frame, frequencies):
    if frame != 1:
        ns = len(seq) + 1 - frame
        frequencies.clear()
        for ii in xrange(ns):
            frequencies[seq[ii:ii + frame]] += 1
        return ns, frequencies
    for nucleo in seq:
        frequencies[nucleo] += 1
    return len(seq), frequencies


def sort_seq(seq, length, frequencies):
    n, frequencies = gen_freq(seq, length, frequencies)

    l = sorted(frequencies.items(), reverse=True, key=lambda (seq,freq): (freq,seq))

    print '\n'.join("%s %.3f" % (st, 100.0*fr/n) for st,fr in l)
    print


def find_seq(seq, s, frequencies):
    n,t = gen_freq(seq, len(s), frequencies)
    print "%d\t%s" % (t.get(s, 0), s)


def main(stdin):
    frequencies = defaultdict(int)
    for line in stdin:
        if line[0] == ">":
            if line[1:3] == "TH":
                break

    seq = []
    seq_append = seq.append
    for line in stdin:
        if line[0] in ">;":
            break
        seq_append(line)
    sequence = "".join(seq).replace('\n','').upper()

    for nl in 1,2:
        sort_seq(sequence, nl, frequencies)

    for se in "GGT GGTA GGTATT GGTATTTTAATT GGTATTTTAATTTATAGT".split():
        find_seq(sequence, se, frequencies)


def __benchmark__(*args):
    # main()  # provide proper input 
    pass 
