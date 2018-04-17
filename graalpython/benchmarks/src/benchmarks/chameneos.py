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
