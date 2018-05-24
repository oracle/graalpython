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

# The Computer Language Benchmarks Game
# http://shootout.alioth.debian.org/
# contributed by Tobias Polzin, translated from Mike Pall's Lua program
# modified by Josh Goldfoot to use ifs for the complement routine
# modified by Heinrich Acker

import sys

N = int(sys.argv[1])
first = second = None
meetings = 0

RED, BLUE, YELLOW = range(1,4)

# Create a very social creature.
def creature(me):
    global N, first, second, meetings
    met = 0
    while 1:
        # Meet another creature.

        # Wait until meeting place clears.
        while second:
            yield None

        other = first
        if other:
            # Hey, I found a new friend!
            second = me
        else:
            # Sniff, nobody here (yet).
            if N <= 0:
                # Uh oh, the mall is closed.
                meetings += met
                yield None

                # The mall was closed, so everyone is faded.
                print meetings
                sys.exit()

            N -= 1
            first = me
            while not second:
                yield None # Wait for another creature.
            other = second

            first = second = None
            yield None

        # perform meeting
        met += 1
        if me != other:
            if me == BLUE:
                me = other == RED and YELLOW or RED
            elif me == RED:
                me = other == BLUE and YELLOW or BLUE
            else:
                me = other == BLUE and RED or BLUE

# Trivial round-robin scheduler.
def schedule(threads):
    while 1:
        for thread in threads:
            thread()

# A bunch of colorful creatures.
threads = [
    creature(BLUE).next,
    creature(RED).next,
    creature(YELLOW).next,
    creature(BLUE).next]

schedule(threads)
