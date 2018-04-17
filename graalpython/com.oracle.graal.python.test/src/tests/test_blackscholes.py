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

# Oct 25, 2015
# numba-benchmarks
# https://github.com/numba/numba-benchmark
#
# originally by Antoine Pitrou
# modified by myq
# remove classes and add pure Black-Scholes model python

"""
Benchmark an implementation of the Black-Scholes model.
"""

import math
import random

# Taken from numba.tests.test_blackscholes

# XXX this data should be shared with bench_cuda.py
# (see https://github.com/spacetelescope/asv/issues/129)
# N = 2 ** 14
N = 2 ** 3

RISKFREE = 0.02
VOLATILITY = 0.30

A1 = 0.31938153
A2 = -0.356563782
A3 = 1.781477937
A4 = -1.821255978
A5 = 1.330274429
RSQRT2PI = 0.39894228040143267793994605993438

callResultGold = [0.0 for i in range(N)]
putResultGold = [0.0 for i in range(N)]

stockPrice = [13.356801635223727, 5.64039610567759, 10.588820572148018, 24.293221812839057, 20.428649486649853, 9.710854254804916, 5.927527607607592, 23.135611932777884]
optionStrike =  [98.83505306010404, 43.489393174574545, 77.89284999691493, 79.53302852348506, 18.451379645332704, 87.64810281277931, 71.94494716607764, 44.10087534124219]
optionYears = [1.1819234720007028, 0.7857559600432207, 3.295422854807452, 5.224639956900903, 1.9369539617729554, 0.8014221409275091, 7.0482700665777305, 5.659486932093904]


def blackscholes(callResult, putResult, stockPrice, optionStrike,
                 optionYears, Riskfree, Volatility):
    S = stockPrice
    X = optionStrike
    T = optionYears
    R = Riskfree
    V = Volatility
    for i in range(len(S)):
        sqrtT = T[i] ** 0.5
        d1 = (math.log(S[i] / X[i]) + (R + 0.5 * V * V) * T[i]) / (V * sqrtT)
        d2 = d1 - V * sqrtT

        # cndd1 = cnd(d1)
        K = 1.0 / (1.0 + 0.2316419 * math.fabs(d1))
        cndd1 = (RSQRT2PI * math.exp(-0.5 * d1 * d1) *
                   (K * (A1 + K * (A2 + K * (A3 + K * (A4 + K * A5))))))
        if d1 > 0:
            cndd1 = 1.0 - cndd1

        # cndd2 = cnd(d2)
        K = 1.0 / (1.0 + 0.2316419 * math.fabs(d2))
        cndd2 = (RSQRT2PI * math.exp(-0.5 * d2 * d2) *
                   (K * (A1 + K * (A2 + K * (A3 + K * (A4 + K * A5))))))
        if d2 > 0:
            cndd2 = 1.0 - cndd2

        expRT = math.exp((-1. * R) * T[i])
        callResult[i] = (S[i] * cndd1 - X[i] * expRT * cndd2)
        putResult[i] = (X[i] * expRT * (1.0 - cndd2) - S[i] * (1.0 - cndd1))



blackscholes(callResultGold, putResultGold, stockPrice, optionStrike,
            optionYears, RISKFREE, VOLATILITY)

delta = (10 ** 10)
crg = [round(i * delta)/delta for i in callResultGold]
prg = [round(i * delta)/delta for i in putResultGold]


def test_it():
    assert crg == [1.2e-09, 0.0, 0.0007193202, 0.6719761796, 4.6701796208, 0.0, 0.0063536033, 2.746302701]
    assert prg == [83.169339134, 37.1708982287, 62.3364751598, 48.0206179035, 1.9917883996, 76.54358492, 56.5644055206, 18.9919438365]
