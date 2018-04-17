# arithmetic ops (partially extracted from spectralnorm)
import time


def docompute(num):
    for i in range(num):
        sum_ = 0.0
        j = 0
        while j < num:
            sum_ += 1.0 / (((i + j) * (i + j + 1) >> 1) + i + 1)
            j += 1

    return sum_


def measure(num):
    print("Start timing...")
    start = time.time()

    for run in range(num):
        sum_ = docompute(10000)  # 10000

    print("sum", sum_)

    duration = "%.3f\n" % (time.time() - start)
    print("arith-binop: " + duration)


print('warming up ...')
for run in range(2000):
    docompute(10)  # 1000

measure(5)
