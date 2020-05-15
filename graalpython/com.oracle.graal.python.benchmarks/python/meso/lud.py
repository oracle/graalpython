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

import math
import random


def lud(a, size):

    for i in range(size):
        for j in range(i, size):
            Sum = a[i * size + j]
            for k in range(i):
                Sum -= a[i * size + k] * a[k * size + j]

            a[i * size + j] = Sum

        for j in range(i + 1, size):
            Sum = a[j * size + i]
            for k in range(i):
                Sum -= a[j * size + k] * a[k * size + i]

            a[j * size + i] = Sum / a[i * size + i]


def lud_verify(m, lu, tmp, matrix_dim, do_print=False):
    for i in range(matrix_dim):
        for j in range(matrix_dim):
            Sum = 0.0
            for k in range(min(i, j) + 1):
                l = 1 if (i == k) else lu[i * matrix_dim + k]
                u = lu[k * matrix_dim + j]
                Sum += l * u
            tmp[i * matrix_dim + j] = Sum

    for i in range(matrix_dim):
        for j in range(matrix_dim):
            if math.fabs(m[i * matrix_dim + j] - tmp[i * matrix_dim + j]) > 0.0001:
                if do_print:
                    print("dismatch at (%d, %d): (o)%f (n)%f" %
                          (i, j, m[i * matrix_dim + j], tmp[i * matrix_dim + j]))
                else:
                    import sys
                    print("Verification failed")
                    sys.exit(1)

# Generate well - conditioned matrix internally  by Ke Wang 2013 / 08 / 07
# 22:20:06


def create_matrix(size):
    lamda = -0.001
    coe = [0.0 for i in range(2 * size - 1)]
    coe_i = 0.0

    for i in range(size):
        coe_i = 10 * math.exp(lamda * i)
        j = size - 1 + i
        coe[j] = coe_i
        j = size - 1 - i
        coe[j] = coe_i

    m = [0. for i in range(size * size)]
    for i in range(size):
        for j in range(size):
            m[i * size + j] = coe[size - 1 - i + j]

    return m


class Data:
    def __init__(self):
        self.m = None
        self.mm = None
        self.tmp = None


data = Data()

default_size = 32


def measure(matrix_dim=default_size):
    print("Starting...")
    lud(data.m, matrix_dim)


def __benchmark__(matrix_dim=32):
    measure(matrix_dim)


def __setup__(matrix_dim=default_size):
    print("Creating matrix internally size=%d" % matrix_dim)
    data.m = create_matrix(matrix_dim)
    data.mm = [data.m[i] for i in range(matrix_dim * matrix_dim)]
    data.tmp = [0.0 for i in range(matrix_dim * matrix_dim)]


def __cleanup__(matrix_dim=default_size):
    print("Verify...")
    lud_verify(data.mm, data.m, data.tmp, matrix_dim)
    # clean up written data
    for i in range(matrix_dim * matrix_dim):
        data.m[i] = data.mm[i]
        data.tmp[i] = 0.0
