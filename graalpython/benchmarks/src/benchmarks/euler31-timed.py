#runas solve()
#unittest.skip recursive generator
#pythran export solve()
# 01/08/14 modified for benchmarking by Wei Zhang
import sys, time

COINS = [1, 2, 5, 10, 20, 50, 100, 200]

# test
def _sum(iterable):
    sum = None
    for i in iterable:
        if sum is None:
            sum = i
        else:
            sum += i

    return sum

def balance(pattern): 
    return _sum(COINS[x]*pattern[x] for x in range(0, len(pattern)))

def gen(pattern, coinnum, num):
    coin = COINS[coinnum]
    for p in range(0, num//coin + 1):
        newpat = pattern[:coinnum] + (p,)
        bal = balance(newpat)

        if bal > num: 
            return
        elif bal == num: 
            yield newpat
        elif coinnum < len(COINS)-1:
            for pat in gen(newpat, coinnum+1, num):
                yield pat

def solve(total):
    '''
    In England the currency is made up of pound, P, and pence, p, and there are eight coins in general circulation:

    1p, 2p, 5p, 10p, 20p, 50p, P1 (100p) and P2 (200p).
    It is possible to make P2 in the following way:

    1 P1 + 1 50p + 2 20p + 1 5p + 1 2p + 3 1p
    How many different ways can P2 be made using any number of coins?
    '''
    return _sum(1 for pat in gen((), 0, total))

def measure():
    input = int(sys.argv[1]) # 200
    for i in range(3):
        solve(input)

    print("Start timing...")
    start = time.time()
    result = solve(input)
    print('total number of different ways: ', result)
    duration = "%.3f\n" % (time.time() - start)
    print("euler31: " + duration)

# warm up
for i in range(2000): # 300
    solve(40)

measure()
