# The Computer Language Benchmarks Game
# http://shootout.alioth.debian.org/
# contributed by Dominique Wahli
# modified by Justin Peel

from sys import stdin
from re import sub, finditer

def main():
    seq = stdin.read()
    
    ilen = len(seq)

    seq = sub('>.*\n|\n', '', seq) 
    clen = len(seq)

    variants = (
          'agggtaaa|tttaccct',
          '[cgt]gggtaaa|tttaccc[acg]',
          'a[act]ggtaaa|tttacc[agt]t',
          'ag[act]gtaaa|tttac[agt]ct',
          'agg[act]taaa|ttta[agt]cct',
          'aggg[acg]aaa|ttt[cgt]ccct',
          'agggt[cgt]aa|tt[acg]accct',
          'agggta[cgt]a|t[acg]taccct',
          'agggtaa[cgt]|[acg]ttaccct')
    for f in variants:
        print f, sum(1 for i in finditer(f, seq))

    subst = {
          'B' : '(c|g|t)', 'D' : '(a|g|t)',   'H' : '(a|c|t)', 'K' : '(g|t)',
          'M' : '(a|c)',   'N' : '(a|c|g|t)', 'R' : '(a|g)',   'S' : '(c|g)',
          'V' : '(a|c|g)', 'W' : '(a|t)',     'Y' : '(c|t)'}
    for f, r in subst.items():
        seq = sub(f, r, seq)

    print
    print ilen
    print clen
    print len(seq)

main()
