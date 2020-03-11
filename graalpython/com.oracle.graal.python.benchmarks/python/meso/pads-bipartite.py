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

# This is PADS, a library of Python Algorithms and Data Structures
# implemented by David Eppstein of the University of California, Irvine.
#
# The current version of PADS may be found at
# <http://www.ics.uci.edu/~eppstein/PADS/>, as individual files or as a
# git repository that may be copied by the command line
#
# 02/24/14 Modified by Wei Zhang


##############################
# DFS
##############################
# Types of edges in DFS traversal.
# The numerical values are used in DepthFirstSearcher, change with care.
forward = 1     # traversing edge (v,w) from v to w
reverse = -1    # returning backwards on (v,w) from w to v
nontree = 0     # edge (v,w) is not part of the DFS tree


def DFS_search(G):
    """
    Generate sequence of triples (v,w,edgetype) for DFS of graph G.
    The subsequence for each root of each tree in the DFS forest starts
    with (root,root,forward) and ends with (root,root,reverse).
    If the initial vertex is given, it is used as the root and vertices
    not reachable from it are not searched.
    """
    visited = set()
    initials = G

    for v in initials:
        if v not in visited:
            yield v,v,forward
            visited.add(v)
            stack = [(v, iter(G[v]))]
            while stack:
                parent,children = stack[-1]
                try:
                    child = next(children)
                    if child in visited:
                        yield parent,child,nontree
                    else:
                        yield parent,child,forward
                        visited.add(child)
                        stack.append((child,iter(G[child])))
                except StopIteration:
                    stack.pop()
                    if stack:
                        yield stack[-1][0],parent,reverse

            yield v,v,reverse


##############################
# Bipartite
##############################
def TwoColor(G):
    """
    Find a bipartition of G, if one exists.
    Raises NonBipartite or returns dict mapping vertices
    to two colors (True and False).
    """
    color = {}
    for v,w,edgetype in DFS_search(G):
        if edgetype is forward:
            color[w] = not color.get(v,False)
        elif edgetype is nontree and color[v] == color[w]:
            return None
    return color


def isBipartite(G):
    """
    Return True if G is bipartite, False otherwise.
    """
    color = TwoColor(G)
    if color == None:
        return False

    return True


def create_cycle_graph(n):
    return {i:[(i-1)%n,(i+1)%n] for i in range(n)}


def main(n):
    graph = create_cycle_graph(n)

    for i in range(20):
        result = isBipartite(graph)
    return result


def measure(num):
    result = main(num)
    print(result)
    

def __benchmark__(num=10000):
    measure(num)
