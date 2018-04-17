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

# simplified euler11 for debugging

NUMS = [
    [8,   2, 22, 97],
    [49, 49, 99, 40],
    [81, 49, 31, 73],
    [52, 70, 95, 23],
]


def seqs(nums, row, col):
    if row + 4 <= len(nums):
        yield list(nums[i][col] for i in range(row, row + 4))

    if col + 4 <= len(nums[row]):
        yield list(nums[row][i] for i in range(col, col + 4))

    if row + 4 <= len(nums) and col + 4 <= len(nums[row]):
        yield list(nums[row+i][col+i] for i in range(0, 4))

    if row + 4 <= len(nums) and col >= 3:
        yield list(nums[row+i][col-i] for i in range(0, 4))


def product(seq):
    # print('seq ', seq)
    n = 1
    for x in seq:
        n = n * x
    return n


def list_seqs(nums):
    for row in range(2):
        for col in range(2):
            # print('row ', row, ' col ', col)
            for seq in seqs(nums, row, col):
                # print('list_seqs ', seq)
                yield seq


def solve():
    ll = [product(seq) for seq in list_seqs(NUMS)]
    # print('solved list ', ll)
    return max(ll)


def test_euler11():
    assert solve() == 9507960
    assert solve() == 9507960
