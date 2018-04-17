# modulo ops
import time


def docompute(num):
    for i in range(num):
        sum = 0
        j = 0
        while j < i:
            if i % 3 == 0:
                temp = 1
            else:
                temp = i % 3

            j += temp
        sum = sum + j

    return sum


def measure(num):
    print("Start timing...")
    start = time.time()

    for run in range(num):
        sum = docompute(5000)  # 5000

    print("sum", sum)

    duration = "%.3f\n" % (time.time() - start)
    print("arith-modulo: " + duration)


print('warming up ...')
for run in range(2000):
    docompute(10)  # 1000

measure(50)
