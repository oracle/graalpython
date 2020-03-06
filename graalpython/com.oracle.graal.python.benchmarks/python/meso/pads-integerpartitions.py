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

"""IntegerPartitions.py

Generate and manipulate partitions of integers into sums of integers.

D. Eppstein, August 2005.
"""


def mckay(n):
    """
    Integer partitions of n, in reverse lexicographic order.
    Note that the generated output consists of the same list object,
    repeated the correct number of times; the caller must leave this
    list unchanged, and must make a copy of any partition that is
    intended to last longer than the next call into the generator.
    The algorithm follows Knuth v4 fasc3 p38 in rough outline.
    """
    if n == 0:
        yield []
    if n <= 0:
        return
    partition = [n]
    last_nonunit = (n > 1) - 1
    while True:
        yield partition
        if last_nonunit < 0:
            return
        if partition[last_nonunit] == 2:
            partition[last_nonunit] = 1
            partition.append(1)
            last_nonunit -= 1
            continue
        replacement = partition[last_nonunit] - 1
        total_replaced = replacement + len(partition) - last_nonunit
        reps,rest = divmod(total_replaced,replacement)
        partition[last_nonunit:] = reps*[replacement]
        if rest:
            partition.append(rest)
        last_nonunit = len(partition) - (partition[-1]==1) - 1


def revlex_partitions(n):
    """
    Integer partitions of n, in reverse lexicographic order.
    The output and asymptotic runtime are the same as mckay(n),
    but the algorithm is different: it involves no division,
    and is simpler than mckay, but uses O(n) extra space for
    a recursive call stack.
    """

    if n == 0:
        yield []
    if n <= 0:
        return
    for p in revlex_partitions(n-1):
        if len(p) == 1 or (len(p) > 1 and p[-1] < p[-2]):
            p[-1] += 1
            yield p
            p[-1] -= 1
        p.append(1)
        yield p
        p.pop()


def lex_partitions(n):
    """Similar to revlex_partitions, but in lexicographic order."""
    if n == 0:
        yield []
    if n <= 0:
        return
    for p in lex_partitions(n-1):
        p.append(1)
        yield p
        p.pop()
        if len(p) == 1 or (len(p) > 1 and p[-1] < p[-2]):
            p[-1] += 1
            yield p
            p[-1] -= 1

partitions = revlex_partitions     # default partition generating algorithm


def binary_partitions(n):
    """
    Generate partitions of n into powers of two, in revlex order.
    Knuth exercise 7.2.1.4.64.
    The average time per output is constant.
    But this doesn't really solve the exercise, because it isn't loopless...
    """

    # Generate the binary representation of n
    if n < 0:
        return
    pow = 1
    sum = 0
    while pow <= n:
        pow <<= 1
    partition = []
    while pow:
        if sum+pow <= n:
            partition.append(pow)
            sum += pow
        pow >>= 1
    
    # Find all partitions of numbers up to n into powers of two > 1,
    # in revlex order, by repeatedly splitting the smallest nonunit power,
    # and replacing the following sequence of 1's by the first revlex
    # partition with maximum power less than the result of the split.
    
    # Time analysis:
    #
    # Each outer iteration increases len(partition) by at most one
    # (only if the power being split is a 2) and each inner iteration
    # in which some ones are replaced by x decreases len(partition),
    # so the number of those inner iterations is less than one per
    # output.
    #
    # Each time a power 2^k is split, it creates two or more 2^{k-1}'s,
    # all of which must eventually be split as well.  So, it S_k denotes
    # the number of times a 2^k is split, and X denotes the total
    # number of outputs generated, then S_k <= X/2^{k-1}.
    # On an outer iteration in which 2^k is split, there will be k
    # inner iterations in which x is halved, so the total number
    # of such inner iterations is <= sum_k k*X/2^{k-1} = O(X).
    #
    # Therefore the overall average time per output is constant.
    
    last_nonunit = len(partition) - 1 - (n&1)
    while True:
        yield partition
        if last_nonunit < 0:
            return
        if partition[last_nonunit] == 2:
            partition[last_nonunit] = 1
            partition.append(1)
            last_nonunit -= 1
            continue
        partition.append(1)

        temp0 = partition[last_nonunit] >> 1
        partition[last_nonunit+1] = temp0
        partition[last_nonunit] = temp0
        x = temp0
        # x = partition[last_nonunit] = partition[last_nonunit+1] = \
        #     partition[last_nonunit] >> 1    # make the split!

        last_nonunit += 1
        while x > 1:
            if len(partition) - last_nonunit - 1 >= x:
                del partition[-x+1:]
                last_nonunit += 1
                partition[last_nonunit] = x
            else:
                x >>= 1


def fixed_length_partitions(n,L):
    """
    Integer partitions of n into L parts, in colex order.
    The algorithm follows Knuth v4 fasc3 p38 in rough outline;
    Knuth credits it to Hindenburg, 1779.
    """
    
    # guard against special cases
    if L == 0:
        if n == 0:
            yield []
        return
    if L == 1:
        if n > 0:
            yield [n]
        return
    if n < L:
        return

    partition = [n - L + 1] + (L-1)*[1]
    while True:
        yield partition
        if partition[0] - 1 > partition[1]:
            partition[0] -= 1
            partition[1] += 1
            continue
        j = 2
        s = partition[0] + partition[1] - 1
        while j < L and partition[j] >= partition[0] - 1:
            s += partition[j]
            j += 1
        if j >= L:
            return
        partition[j] = x = partition[j] + 1
        j -= 1
        while j > 0:
            partition[j] = x
            s -= x
            j -= 1
        partition[0] = s


def conjugate(p):
    """
    Find the conjugate of a partition.
    E.g. len(p) = max(conjugate(p)) and vice versa.
    """
    result = []
    j = len(p)
    if j <= 0:
        return result
    while True:
        result.append(j)
        while len(result) >= p[j-1]:
            j -= 1
            if j == 0:
                return result
    

def main(n):
    for p in binary_partitions(n):
        ret = len(p)

    return ret


def measure(num):
    result = main(num)
    print(result)
    

def __benchmark__(num=700):
    measure(num)
