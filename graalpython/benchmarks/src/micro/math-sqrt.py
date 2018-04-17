# gulfem 01/09/14
# builtin function len()
from math import sqrt
import time


def call_sqrt(num):
    value = 0
    for i in range(num):
        value = sqrt(i)

    return value


def measure():
    print("Start timing...")
    start = time.time()

    result = call_sqrt(500000000)

    print("Sqrt", result)

    duration = "%.3f\n" % (time.time() - start)
    print("math-sqrt: " + duration)


# warm up
print('warming up ...')
for run in range(3000):
    call_sqrt(run)

measure()
