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

from array import array


def fannkuch(n):
    p = array('i', range(n))
    q = array('i', range(n))
    s = array('i', range(n))
    sign = 1
    maxflips = 0
    summ = 0
    m = n - 1
    while True:
        q0 = p[0]
        if q0:
            i = 1
            while i < n:
                q[i] = p[i]
                i += 1
            flips = 1
            while True:
                qq = q[q0]
                if not qq:
                    summ += sign*flips
                    if flips > maxflips:
                        maxflips = flips
                    break
                q[q0] = q0;
                if q0 >= 3:
                    i = 1
                    j = q0 - 1
                    while True:
                        q[i], q[j] = q[j], q[i]
                        i += 1
                        j -= 1
                        if i >= j:
                            break
                q0 = qq
                flips += 1
        if sign == 1:
            p[1], p[0] = p[0], p[1]
            sign = -1
        else:
            p[1], p[2] = p[2], p[1]
            sign = 1
            i = 2
            while i < n:
                sx = s[i]
                if sx != 0:
                    s[i] = sx - 1
                    break
                if i == m:
                    return summ, maxflips
                s[i] = i
                t = p[0]
                j = 0
                while j <= i:
                    p[j] = p[j + 1]
                    j += 1
                p[i + 1] = t
                i += 1


def measure(num):
    sum, maxflips = fannkuch(num)
    print(sum)
    print("Pfannkuchen(%d) = %d" % (num, maxflips))


def __benchmark__(num=11):
    measure(num)
