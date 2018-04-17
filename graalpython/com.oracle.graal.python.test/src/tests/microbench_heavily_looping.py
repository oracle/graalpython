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

# Oct 19, 2015
# heavily looping function
# https://www.wakari.io/sharing/bundle/yves/Continuum_N_Body_Simulation_Numba
#
# originally by Dr. Yves J. Hilpisch
# modified by myq

import time
def f(n):
    t0 = time.time()
    result = 0.0
    for i in range(n):
        for j in range(n * i):
            result += sin(pi / 2)
    return int(result), time.time()-t0

    n = 250
res_py = f(n)

print "Number of Loops        %8d" % res_py[0]
print "Time in Sec for Python %8.3f" % res_py[1]

import numba as nb
f_nb = nb.autojit(f)

res_nb = f_nb(n)

print "Number of Loops        %8d" % res_nb[0]
print "Time in Sec for Python %8.3f" % res_nb[1]

print "Number of Loops        %8d" % res_py[0]
print "Speed-up of Numba      %8d" % (res_py[1] / res_nb[1])
