# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import random
import math


def srad_1(q0sqr, cols, rows, J, dN, dS, dE, dW, c):
    for i in range(rows):
        for j in range(cols):
            k = i * cols + j  # current index
            Jc = J[k]
            # directional derivates
            n = i - 1 if i > 0 else 0
            s = i + 1 if i < (rows - 1) else (rows - 1)
            w = j - 1 if j > 0 else 0
            e = j + 1 if j < (cols - 1) else (cols - 1)

            dn = J[n * cols + j] - Jc
            ds = J[s * cols + j] - Jc
            dw = J[i * cols + w] - Jc
            de = J[i * cols + e] - Jc
            dN[k] = dn
            dS[k] = ds
            dE[k] = de
            dW[k] = dw
            G2 = (dn*dn + ds*ds + dw*dw + de*de) / (Jc*Jc)
            L = (dn + ds + dw + de) / Jc
            num = (0.5*G2) - ((1.0/16.0)*(L*L))
            den = 1 + (.25*L)
            qsqr = num/(den*den)
            # diffusion coefficent (equ 33)
            den = (qsqr-q0sqr) / (q0sqr * (1+q0sqr))
            v = 1.0 / (1.0+den)
            # saturate diffusion coefficent
            v = v if v > 0. else 0.
            v = v if v < 1. else 1.
            c[k] = v


def srad_2(cols, rows, J, dN, dS, dE, dW, c, Lambda):
    for i in range(rows):
        for j in range(cols):
            k = i * cols + j  # current index
            # diffusion coefficent
            s = i + 1 if i < (rows - 1) else (rows - 1)
            e = j + 1 if j < (cols - 1) else (cols - 1)
            cN = c[k]
            cS = c[s * cols + j]
            cW = c[k]
            cE = c[i * cols + e]
            # divergence (equ 58)
            D = cN * dN[k] + cS * dS[k] + cW * dW[k] + cE * dE[k]
            # image update (equ 61)
            J[k] += 0.25*Lambda*D


def srad(nIter, size_R, cols, rows, J, dN, dS, dE, dW, c, r1, r2, c1, c2, Lambda):
    for ii in range(nIter):
        Sum = 0.
        Sum2 = 0.
        for i in range(r1, r2+1):
            for j in range(c1, c2+1):
                tmp = J[i * cols + j]
                Sum += tmp
                Sum2 += tmp*tmp

        meanROI = Sum / size_R
        varROI = (Sum2 / size_R) - meanROI*meanROI
        q0sqr = varROI / (meanROI*meanROI)
        srad_1(q0sqr, cols, rows, J, dN, dS, dE, dW, c)

        srad_2(cols, rows, J, dN, dS, dE, dW, c, Lambda)


class Data:
    def __init__(self):
        self.dN = None
        self.dS = None
        self.dW = None
        self.dE = None
        self.J = None
        self.JJ = None
        self.c = None


data = Data()

default_size = 256


def measure(nIter, cols=default_size, rows=default_size, r1=0, r2=127, c1=0, c2=127, Lambda=0.5):

    print("Start the SRAD main loop")
    size_R = (r2 - r1 + 1) * (c2 - c1 + 1)
    srad(nIter, size_R, cols, rows, data.J, data.dN, data.dS,
         data.dE, data.dW, data.c, r1, r2, c1, c2, Lambda)


def __benchmark__(nIter=100):
    measure(nIter)


def __setup__(nIter, cols=default_size, rows=default_size):
    size_I = cols * rows

    print("Initializing...")
    data.dN = [0. for j in range(size_I)]
    data.dS = [0. for j in range(size_I)]
    data.dW = [0. for j in range(size_I)]
    data.dE = [0. for j in range(size_I)]

    print("Randomizing the input matrix")
    random.seed(7)
    data.J = [float(math.exp(random.random()/255)) for j in range(size_I)]
    data.JJ = [data.J[j] for j in range(size_I)]
    data.c = [0. for j in range(size_I)]


def __cleanup__(nIter, cols=default_size, rows=default_size):
    # clean up written data
    for i in range(cols * rows):
        data.J[i] = data.JJ[i]
