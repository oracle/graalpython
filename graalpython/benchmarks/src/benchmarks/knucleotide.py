# The Computer Language Benchmarks Game
# http://shootout.alioth.debian.org/
#
# submitted by Ian Osgood
# modified by Sokolov Yura
# modified by bearophile
# modified by Justin Peel

from sys import stdin
from collections import defaultdict

def gen_freq(seq, frame, frequencies):
    if frame != 1:
        ns = len(seq) + 1 - frame
        frequencies.clear()
        for ii in xrange(ns):
            frequencies[seq[ii:ii + frame]] += 1
        return ns, frequencies
    for nucleo in seq:
        frequencies[nucleo] += 1
    return len(seq), frequencies

def sort_seq(seq, length, frequencies):
    n, frequencies = gen_freq(seq, length, frequencies)

    l = sorted(frequencies.items(), reverse=True, key=lambda (seq,freq): (freq,seq))

    print '\n'.join("%s %.3f" % (st, 100.0*fr/n) for st,fr in l)
    print


def find_seq(seq, s, frequencies):
    n,t = gen_freq(seq, len(s), frequencies)
    print "%d\t%s" % (t.get(s, 0), s)

def main():
    frequencies = defaultdict(int)
    for line in stdin:
        if line[0] == ">":
            if line[1:3] == "TH":
                break

    seq = []
    seq_append = seq.append
    for line in stdin:
        if line[0] in ">;":
            break
        seq_append( line )
    sequence = "".join(seq).replace('\n','').upper()

    for nl in 1,2:
        sort_seq(sequence, nl, frequencies)

    for se in "GGT GGTA GGTATT GGTATTTTAATT GGTATTTTAATTTATAGT".split():
        find_seq(sequence, se, frequencies)

main()
