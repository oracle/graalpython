"""Lyndon.py
Algorithms on strings and sequences based on Lyndon words.
David Eppstein, October 2011."""

import unittest
import sys, time
# from Eratosthenes import MoebiusFunction

def LengthLimitedLyndonWords(s,n):
    """Generate nonempty Lyndon words of length <= n over an s-symbol alphabet.
    The words are generated in lexicographic order, using an algorithm from
    J.-P. Duval, Theor. Comput. Sci. 1988, doi:10.1016/0304-3975(88)90113-2.
    As shown by Berstel and Pocchiola, it takes constant average time
    per generated word."""
    w = [-1]                            # set up for first increment
    while w:
        w[-1] += 1                      # increment the last non-z symbol
        yield w
        m = len(w)
        while len(w) < n:               # repeat word to fill exactly n syms
            w.append(w[-m])
        while len(w) > 0 and w[-1] == s - 1:     # delete trailing z's
            w.pop()

def LyndonWordsWithLength(s,n):
    """Generate Lyndon words of length exactly n over an s-symbol alphabet.
    Since nearly half of the outputs of LengthLimitedLyndonWords(s,n)
    have the desired length, it again takes constant average time per word."""
    if n == 0:
        yield []    # the empty word is a special case not handled by main alg
    for w in LengthLimitedLyndonWords(s,n):
        if len(w) == n:
            yield w

def LyndonWords(s):
    """Generate all Lyndon words over an s-symbol alphabet.
    The generation order is by length, then lexicographic within each length."""
    n = 0
    while True:
        for w in LyndonWordsWithLength(s,n):
            yield w
        n += 1

def DeBruijnSequence(s,n):
    """Generate a De Bruijn sequence for words of length n over s symbols
    by concatenating together in lexicographic order the Lyndon words
    whose lengths divide n. The output length will be s^n.
    Because nearly half of the generated sequences will have length
    exactly n, the algorithm will take O(s^n/n) steps, and the bulk
    of the time will be spent in sequence concatenation."""
    
    output = []
    for w in LengthLimitedLyndonWords(s,n):
        if n % len(w) == 0:
            output += w
    return output

def CountLyndonWords(s,n):
    """The number of length-n Lyndon words over s symbols."""
    if n == 0:
        return 1
    total = 0
    for i in range(1,n+1):
        if n%i == 0:
            total += MoebiusFunction(n/i) * s**i
    return total//n

def ChenFoxLyndonBreakpoints(s):
    """Find starting positions of Chen-Fox-Lyndon decomposition of s.
    The decomposition is a set of Lyndon words that start at 0 and
    continue until the next position. 0 itself is not output, but
    the final breakpoint at the end of s is. The argument s must be
    of a type that can be indexed (e.g. a list, tuple, or string).
    The algorithm follows Duval, J. Algorithms 1983, but uses 0-based
    indexing rather than Duval's choice of 1-based indexing."""
    k = 0
    while k < len(s):
        i,j = k,k+1
        while j < len(s) and s[i] <= s[j]:
            i = (s[i] == s[j]) and i+1 or k     # Python cond?yes:no syntax
            j += 1
        while k < i+1:
            k += j-i
            yield k

def ChenFoxLyndon(s):
    """Decompose s into Lyndon words according to the Chen-Fox-Lyndon theorem.
    The arguments are the same as for ChenFoxLyndonBreakpoints but the
    return values are subsequences of s rather than indices of breakpoints."""
    old = 0
    for k in ChenFoxLyndonBreakpoints(s):
        yield s[old:k]
        old = k

def SmallestSuffix(s):
    """Find the suffix of s that is smallest in lexicographic order."""
    for w in ChenFoxLyndon(s):
        pass
    return w

def SmallestRotation(s):
    """Find the rotation of s that is smallest in lexicographic order.
    Duval 1983 describes how to modify his algorithm to do so but I think
    it's cleaner and more general to work from the ChenFoxLyndon output."""
    prev,rep = None,0
    for w in ChenFoxLyndon(s+s):
        if w == prev:
            rep += 1
        else:
            prev,rep = w,1
        if len(w)*rep == len(s):
            return w*rep
    raise Exception("Reached end of factorization with no shortest rotation")

def isLyndonWord(s):
    """Is the given sequence a Lyndon word?"""
    if len(s) == 0:
        return True
    return ChenFoxLyndonBreakpoints(s).next() == len(s)

# If run standalone, perform unit tests
class LyndonTest(unittest.TestCase):
    def testCount(self):
        """Test that we count Lyndon words correctly."""
        for s in range(2,7):
            for n in range(1,6):
                self.assertEqual(CountLyndonWords(s,n),
                    len(list(LyndonWordsWithLength(s,n))))

    def testOrder(self):
        """Test that we generate Lyndon words in lexicographic order."""
        for s in range(2,7):
            for n in range(1,6):
                prev = []
                for x in LengthLimitedLyndonWords(s,n):
                    self.assert_(prev < x)
                    prev = list(x)

    def testSubsequence(self):
        """Test that words of length n-1 are a subsequence of length n."""
        for s in range(2,7):
            for n in range(2,6):
                smaller = LengthLimitedLyndonWords(s,n-1)
                for x in LengthLimitedLyndonWords(s,n):
                    if len(x) < n:
                        self.assertEqual(x,smaller.next())
    
    def testIsLyndon(self):
        """Test that the words we generate are Lyndon words."""
        for s in range(2,7):
            for n in range(2,6):
                for w in LengthLimitedLyndonWords(s,n):
                    self.assertEqual(isLyndonWord(w), True)
    
    def testNotLyndon(self):
        """Test that words that are not Lyndon words aren't claimed to be."""
        nl = sum(1 for i in range(8**4) if isLyndonWord("%04o" % i))
        self.assertEqual(nl,CountLyndonWords(8,4))

    def testDeBruijn(self):
        """Test that the De Bruijn sequence is correct."""
        for s in range(2,7):
            for n in range(1,6):
                db = DeBruijnSequence(s,n)
                self.assertEqual(len(db), s**n)
                db = db + db    # duplicate so we can wrap easier
                subs = set(tuple(db[i:i+n]) for i in range(s**n))
                self.assertEqual(len(subs), s**n)

# if __name__ == "__main__":
#     unittest.main()

def main(n):
    count = 0
    for w in LyndonWords(n):
        count += 1
        if count == n:
            break

    return w

def measure():
    input = int(sys.argv[1]) #100000000
    for i in range(3):
        main(input)

    print("Start timing...")
    start = time.time()
    result = main(input)
    print(result)
    duration = "%.3f\n" % (time.time() - start)
    print("pads-lyndon: " + duration)

for i in range(5000):
    main(10000)

measure()
