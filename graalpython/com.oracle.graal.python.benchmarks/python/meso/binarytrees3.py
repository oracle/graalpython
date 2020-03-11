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
# contributed by Antoine Pitrou
# modified by Dominique Wahli
# modified by Heinrich Acker


def make_tree(item, depth):
    if not depth: return item, None, None
    item2 = item + item
    depth -= 1
    return item, make_tree(item2 - 1, depth), make_tree(item2, depth)


def check_tree(xxx_todo_changeme):
    (item, left, right) = xxx_todo_changeme
    if not left: return item
    return item + check_tree(left) - check_tree(right)


def main(num):
    min_depth = 4
    max_depth = max(min_depth + 2, num)
    stretch_depth = max_depth + 1

    print("stretch tree of depth %d\t check:" % stretch_depth, check_tree(make_tree(0, stretch_depth)))

    long_lived_tree = make_tree(0, max_depth)

    iterations = 2**max_depth

    for depth in range(min_depth, stretch_depth, 2):

        check = 0
        for i in range(1, iterations + 1):
            check += check_tree(make_tree(i, depth)) + check_tree(make_tree(-i, depth))

        print("%d\t trees of depth %d\t check:" % (iterations * 2, depth), check)
        iterations //= 4

    print("long lived tree of depth %d\t check:" % max_depth, check_tree(long_lived_tree))


def __benchmark__(num=10):
    main(num)
