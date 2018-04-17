import sys
import math
import time

class Natural:
    n = 2

    def next(self):
        r = self.n
        self.n = self.n + 1
        return r

class Filter:
    def __init__(self, n):
        self.number = n
        self.next = None
        self.last = self

    def acceptAndAdd(self, n):
        filter = self
        sqrt = math.sqrt(n)
        while True:
            if n % filter.number == 0:
                return False
            if filter.number > sqrt:
                break
            filter = filter.next

        newFilter = Filter(n)
        self.last.next = newFilter
        self.last = newFilter
        return True

class Primes:
    def __init__(self, natural):
        self.natural = natural
        self.filter = None

    def next(self):
        while True:
            n = self.natural.next()
            if (self.filter == None):
                self.filter = Filter(n)
                return n
            if (self.filter.acceptAndAdd(n)):
                return n

def measure(prntCnt, upto):
    primes = Primes(Natural())
    start = time.time()
    cnt = 0
    res = -1
    while cnt < upto:
        res = primes.next()
        cnt = cnt + 1
        if (cnt % prntCnt == 0):
            print("Computed %s primes in %s s. Last one is %s" % (cnt, time.time() - start, res))
            prntCnt = prntCnt * 2

    return time.time() - start

print('warming up')
count = int(sys.argv[1]) if len(sys.argv) > 1 else 100000
measure(97, count)

print('warming up again')
measure(count, count)
measure(count, count)
measure(count, count)
measure(count, count)
measure(count, count)
measure(count, count)

print('timing')
took = measure(count, count)
print("sieve: %s seconds" % took)
