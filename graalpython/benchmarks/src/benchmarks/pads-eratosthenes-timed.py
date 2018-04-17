# This is PADS, a library of Python Algorithms and Data Structures
# implemented by David Eppstein of the University of California, Irvine.
#
# The current version of PADS may be found at
# <http://www.ics.uci.edu/~eppstein/PADS/>, as individual files or as a
# git repository that may be copied by the command line
#
# 02/24/14 Modified by Wei Zhang

import sys, time

def updateDict(dict, primaryKey, secondKey, val):
	if primaryKey in dict:
		dict[primaryKey][secondKey] = val
	else:
		dict[primaryKey] = {secondKey : val}

def FactoredIntegers():
    """
    Generate pairs n,F where F is the prime factorization of n.
    F is represented as a dictionary in which each prime factor of n
    is a key and the exponent of that prime is the corresponding value.
    """
    yield 1,{}
    i = 2
    factorization = {}
    while True:
        if i not in factorization:  # prime
            F = {i:1}
            yield i,F
            factorization[2*i] = F
        elif len(factorization[i]) == 1:    # prime power
            p, x = next(iter(factorization[i].items()))

            F = {p:x+1}
            yield i,F
            factorization[2*i] = F
            updateDict(factorization, i+p**x, p, x)
            del factorization[i]
        else:
            yield i,factorization[i]
            for p,x in factorization[i].items():
                q = p**x
                iq = i+q
                if iq in factorization and p in factorization[iq]:
                    iq += p**x  # skip higher power of p
                updateDict(factorization, iq, p, x)

            del factorization[i]
        i += 1

def isPracticalFactorization(f):
    """Test whether f is the factorization of a practical number."""
    f = list(f.items())    
    f.sort()
    sigma = 1
    for p,x in f:
        if sigma < p - 1:
            return False
        sigma *= (p**(x+1)-1)//(p-1)
    return True

def PracticalNumbers():
    """Generate the sequence of practical (or panarithmic) numbers."""
    for x,f in FactoredIntegers():
        if isPracticalFactorization(f):
            yield x

def main(n):
    """Test that the first few practical nos are generated correctly."""
    # G = PracticalNumbers()
    # for p in [1,2,4,6,8,12,16,18,20,24,28,30,32,36]:
        # self.assertEqual(p,G.next())
    # nums = []
    # for i in range(10):
    # 	nums.append(next(G))
    # print(nums)

    nums = []
    for num in PracticalNumbers():
        nums.append(num)
        if len(nums) == n:
            break;

    return nums[-1]

def measure():
    input = int(sys.argv[1])
    for i in range(3):
        main(input)

    print("Start timing...")
    start = time.time()
    result = main(input)
    print(result)
    duration = "%.3f\n" % (time.time() - start)
    print("pads-eratosthenes: " + duration)

# warmup
for i in range(100):
    main(1000)

measure()