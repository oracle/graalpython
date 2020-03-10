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

# The Computer Language Benchmarks Game
# http://shootout.alioth.debian.org/
#
# Contributed by Sebastien Loisel
# Fixed by Isaac Gouy
# Sped up by Josh Goldfoot
# Dirtily sped up by Simon Descarpentries
# Sped up by Joseph LaFata

from array import array
from math import sqrt


def eval_A(i, j):
    return 1.0 / (((i + j) * (i + j + 1) >> 1) + i + 1)


def eval_A_times_u(u, resulted_list):
    u_len = len(u)
    #local_eval_A = eval_A

    for i in range (u_len):
        partial_sum = 0

        j = 0
        while j < u_len:
            partial_sum += eval_A(i, j) * u[j]
            j += 1

        resulted_list[i] = partial_sum


def eval_At_times_u(u, resulted_list):
    u_len = len(u)
    #local_eval_A = eval_A

    for i in range(u_len):
        partial_sum = 0

        j = 0
        while j < u_len:
            partial_sum += eval_A(j, i) * u[j]
            j += 1

        resulted_list[i] = partial_sum


def eval_AtA_times_u(u, out, tmp):
    eval_A_times_u(u, tmp)
    eval_At_times_u(tmp, out)


def main(n):
    u = array("d", [1]) * n
    v = array("d", [1]) * n
    tmp = array("d", [1]) * n
    #local_eval_AtA_times_u = eval_AtA_times_u

    for dummy in range(10):
        eval_AtA_times_u(u, v, tmp)
        eval_AtA_times_u(v, u, tmp)

    vBv = vv = 0

    for ue, ve in zip(u, v):
        vBv += ue * ve
        vv  += ve * ve

    #print("%0.9f" % (sqrt(vBv/vv)))
    sqrt(vBv/vv)


def measure(num):
    main(num)
    

def __benchmark__(num=3000):
    measure(num)
