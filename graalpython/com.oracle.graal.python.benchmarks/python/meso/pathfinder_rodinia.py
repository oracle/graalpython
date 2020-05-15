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


import time
import random

M_SEED = 9


def pathfinder_cols(t, wall, src, cols, dst):
    for n in range(cols):
        Min = src[n]
        if (n > 0):
            Min = min(Min, src[n - 1])
        if (n < cols - 1):
            Min = min(Min, src[n + 1])
        dst[n] = wall[(t + 1) * cols + n] + Min


def pathfinder(wall, src, rows, cols, result):
    dst = result
    ssrc = src
    for t in range(rows - 1):
        dst, ssrc = ssrc, dst
        pathfinder_cols(t, wall, ssrc, cols, dst)


class Data:
    def __init__(self):
        self.wall = None
        self.result = None
        self.src = None


data = Data()

default_cols = 100000
default_rows = 100


def measure(iteration, cols=default_cols, rows=default_rows):
    print("Starting...")
    for i in range(iteration):
        pathfinder(data.wall, data.src, rows, cols, data.result)


def __benchmark__(iteration=10):
    measure(iteration)


def __setup__(iteration, cols=default_cols, rows=default_rows):
    print("Initializing...")
    random.seed(M_SEED)
    data.wall = [random.randint(0, 10) for j in range(cols * rows)]
    data.result = [data.wall[j] for j in range(cols)]
    data.src = [0 for j in range(cols)]


def __cleanup__(iteration, cols=default_cols, rows=default_rows):
    # clean up written data
    for i in range(cols):
        data.result[i] = data.wall[i]
        data.src[i] = 0
