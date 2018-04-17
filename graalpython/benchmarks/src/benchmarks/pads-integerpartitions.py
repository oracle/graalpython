"""IntegerPartitions.py

Generate and manipulate partitions of integers into sums of integers.

D. Eppstein, August 2005.
"""

import unittest
import sys, time

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
    
# If run standalone, perform unit tests

class PartitionTest(unittest.TestCase):
    counts = [1,1,2,3,5,7,11,15,22,30,42,56,77,101,135]

    def testCounts(self):
        """Check that each generator has the right number of outputs."""
        for n in range(len(self.counts)):
            self.assertEqual(self.counts[n],len(list(mckay(n))))
            self.assertEqual(self.counts[n],len(list(lex_partitions(n))))
            self.assertEqual(self.counts[n],len(list(revlex_partitions(n))))

    def testSums(self):
        """Check that all outputs are partitions of the input."""
        for n in range(len(self.counts)):
            for p in mckay(n):
                self.assertEqual(n,sum(p))
            for p in revlex_partitions(n):
                self.assertEqual(n,sum(p))
            for p in lex_partitions(n):
                self.assertEqual(n,sum(p))
    
    def testRevLex(self):
        """Check that the revlex generators' outputs are in revlex order."""
        for n in range(len(self.counts)):
            last = [n+1]
            for p in mckay(n):
                self.assert_(last > p)
                last = list(p)  # make less-mutable copy
            last = [n+1]
            for p in revlex_partitions(n):
                self.assert_(last > p)
                last = list(p)  # make less-mutable copy

    def testLex(self):
        """Check that the lex generator's outputs are in lex order."""
        for n in range(1,len(self.counts)):
            last = []
            for p in lex_partitions(n):
                if not (last < p):
                    print("last:",last,"p:",p)
                self.assert_(last < p)
                last = list(p)  # make less-mutable copy

    def testRange(self):
        """Check that all numbers in output partitions are in range."""
        for n in range(len(self.counts)):
            for p in mckay(n):
                for x in p:
                    self.assert_(0 < x <= n)
            for p in lex_partitions(n):
                for x in p:
                    self.assert_(0 < x <= n)
            for p in revlex_partitions(n):
                for x in p:
                    self.assert_(0 < x <= n)
    
    def testFixedLength(self):
        """Check that the fixed length partition outputs are correct."""
        for n in range(len(self.counts)):
            pn = [list(p) for p in revlex_partitions(n)]
            pn.sort()
            np = 0
            for L in range(n+1):
                pnL = [list(p) for p in fixed_length_partitions(n,L)]
                pnL.sort()
                np += len(pnL)
                self.assertEqual(pnL,[p for p in pn if len(p) == L])
            self.assertEqual(np,len(pn))
                
    def testConjugatePartition(self):
        """Check that conjugating a partition forms another partition."""
        for n in range(len(self.counts)):
            for p in partitions(n):
                c = conjugate(p)
                for x in c:
                    self.assert_(0 < x <= n)
                self.assertEqual(sum(c),n)

    def testConjugateInvolution(self):
        """Check that double conjugation returns the same partition."""
        for n in range(len(self.counts)):
            for p in partitions(n):
                self.assertEqual(p,conjugate(conjugate(p)))

    def testConjugateMaxLen(self):
        """Check the max-length reversing property of conjugation."""
        for n in range(1,len(self.counts)):
            for p in partitions(n):
                self.assertEqual(len(p),max(conjugate(p)))

    def testBinary(self):
        """Test that the binary partitions are generated correctly."""
        for n in range(len(self.counts)):
            binaries = []
            for p in partitions(n):
                for x in p:
                    if x & (x - 1):
                        break
                else:
                    binaries.append(list(p))
            self.assertEqual(binaries,[list(p) for p in binary_partitions(n)])

# if __name__ == "__main__":
#     unittest.main()

def main(n):
    for p in binary_partitions(n):
        ret = len(p)

    return ret


def measure():
    input = int(sys.argv[1]) #700
    for i in range(3):
        main(input)

    print("Start timing...")
    start = time.time()
    result = main(input)
    print(result)
    duration = "%.3f\n" % (time.time() - start)
    print("pads-partitions: " + duration)

for i in range(50):
    main(400)

measure()
