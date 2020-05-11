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
# contributed by Daniel Nanz, 2008-08-21
# 2to3
from __future__ import print_function
from bisect import bisect
import time

w, h = 5, 10
dir_no = 6
S, E = w * h, 2
SE = S + (E // 2)
SW = SE - E
W, NW, NE = -E, -SE, -SW


def rotate(ido, rd={E: NE, NE: NW, NW: W, W: SW, SW: SE, SE: E}):
    return [rd[o] for o in ido]


def flip(ido, fd={E: E, NE: SE, NW: SW, W: W, SW: NW, SE: NE}):
    return [fd[o] for o in ido]


def permute(ido, r_ido, rotate=rotate, flip=flip):

    ps = [ido]
    for r in range(dir_no - 1):
        ps.append(rotate(ps[-1]))
        if ido == r_ido:                 # C2-symmetry
            ps = ps[0:dir_no//2]
    for pp in ps[:]:
        ps.append(flip(pp))
    return ps


def convert(ido):
    '''incremental direction offsets -> "coordinate offsets" '''
    out = [0]
    for o in ido:
        out.append(out[-1] + o)
    return list(set(out))


def get_footprints(board, cti, pieces):

    fps = [[[] for ip in range(len(pieces))] for ci in range(len(board))]
    for c in board:
        for pi, p in enumerate(pieces):
            for pp in p:
                fp = frozenset(cti[c + o] for o in pp if (c + o) in cti)
                if len(fp) == 5:
                    fps[min(fp)][pi].append(fp)
    return fps


def get_senh(board, cti):
    '''-> south-east neighborhood'''
    se_nh = []
    nh = [E, SW, SE]
    for c in board:
        se_nh.append(frozenset(cti[c + o] for o in nh if (c + o) in cti))
    return se_nh


def get_puzzle(w=w, h=h):

    board = [E*x + S*y + (y%2) for y in range(h) for x in range(w)]
    cti = dict((board[i], i) for i in range(len(board)))

    idos = [[E, E, E, SE],         # incremental direction offsets
            [SE, SW, W, SW],
            [W, W, SW, SE],
            [E, E, SW, SE],
            [NW, W, NW, SE, SW],
            [E, E, NE, W],
            [NW, NE, NE, W],
            [NE, SE, E, NE],
            [SE, SE, E, SE],
            [E, NW, NW, NW]]

    perms = (permute(p, idos[3]) for p in idos)    # restrict piece 4
    pieces = [[convert(pp) for pp in p] for p in perms]
    return (board, cti, pieces)


def print_board(board, w=w, h=h):

    for y in range(h):
        for x in range(w):
            print(board[x + y * w], end=' ')
        print('')
        if y % 2 == 0:
            print('', end=' ')
    print()


board, cti, pieces = get_puzzle()
fps = get_footprints(board, cti, pieces)
se_nh = get_senh(board, cti)


def solve(n, i_min, free, curr_board, pieces_left, solutions,
          fps=fps, se_nh=se_nh, bisect=bisect):

    fp_i_cands = fps[i_min]
    for p in pieces_left:
        fp_cands = fp_i_cands[p]
        for fp in fp_cands:
            if fp <= free:
                n_curr_board = curr_board[:]
                for ci in fp:
                    n_curr_board[ci] = p
                if len(pieces_left) > 1:
                    n_free = free - fp
                    n_i_min = min(n_free)
                    if len(n_free & se_nh[n_i_min]) > 0:
                        n_pieces_left = pieces_left[:]
                        n_pieces_left.remove(p)
                        solve(n, n_i_min, n_free, n_curr_board,
                              n_pieces_left, solutions)
                else:
                    s = ''.join(map(str, n_curr_board))
                    solutions.insert(bisect(solutions, s), s)
                    rs = s[::-1]
                    solutions.insert(bisect(solutions, rs), rs)
                    if len(solutions) >= n:
                        return
        if len(solutions) >= n:
            return
    return


def main(n):
    free = frozenset(range(len(board)))
    curr_board = [-1] * len(board)
    pieces_left = list(range(len(pieces)))
    solutions = []
    solve(n, 0, free, curr_board, pieces_left, solutions)
    print(len(solutions),  'solutions found\n')
    for i in (0, -1): print_board(solutions[i])


def measure(num):
    main(num)


def __benchmark__(num=2098):
    measure(num)
