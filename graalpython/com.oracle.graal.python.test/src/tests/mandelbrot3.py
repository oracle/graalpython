# Copyright 2008-2010 Isaac Gouy
# Copyright (c) 2013, 2014, Regents of the University of California
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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
# contributed by Tupteq
# 2to3 - fixed by Daniele Varrazzo

#from __future__ import print_function
import sys, time

def main():
    # cout = sys.stdout.write
    size = 10000
    # size = int(sys.argv[1])
    xr_size = range(size)
    xr_iter = range(50)
    # cout("P4\n%d %d\n" % (size, size))
    # print("P4\n%d %d\n" % (size, size))

    # byte_acc_inc_yx = [[0 for i in range(size)] for i in range(size)]

    size = float(size)
    for y in xr_size:
        bit = 128
        byte_acc = 0
        fy = 2j * y / size - 1j
        for x in xr_size:
            z = 0j
            c = 2. * x / size - 1.5 + fy
            for i in xr_iter:
                z = z * z + c
                if abs(z) >= 2.0:
                    # byte_acc_inc_yx[y][x] = 1
                    break
            else:
                # byte_acc_inc_yx[y][x] = 0
                byte_acc += bit

            if bit > 1:
                bit >>= 1
            else:
                # cout(chr(byte_acc))
                # byte_acc_inc_yx[y][x] = byte_acc
                bit = 128
                byte_acc = 0
        if bit != 128:
            # cout(chr(byte_acc))
            bit = 128
            byte_acc = 0

    # print("byte_acc_inc = ", sum([sum(i) for i in byte_acc_inc_yx]))
    # s = ""
    # for y in xr_size:
    #     for x in xr_size:
    #         if byte_acc_inc_yx[y][x] == 0:
    #             s += "*"
    #         else:
    #             s += " "
    #     s += "\n"
    # print(s)
    # with open("mandelbrot.pm", "a") as myfile:
    #     myfile.write("P4\n%d %d\n" % (size, size))
    #     for y in xr_size:
    #         for x in xr_size:
    #             myfile.write(chr(byte_acc_inc_yx[y][x]))
    #         myfile.write(chr(byte_acc_inc[y]))
    # print(byte_acc_inc_y)
start = time.time()
main()
duration = "%.3f\n" % (time.time() - start)
print("mandelbrot: " + duration)
