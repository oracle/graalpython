# micro benchmark: simple for range loop
import time

iteration = 50000  # 50000


def add(left, right):
    return left + right


def sumitup(iteration):
    total = 0
    for i in range(iteration):
        total = add(total, i)

    return total


def measure(num):
    print("Start timing...")
    start = time.time()

    for i in range(num):  # 50000
        sumitup(iteration)

    print(sumitup(iteration))
    duration = "%.3f\n" % (time.time() - start)
    print("for-range: " + duration)


print('warming up ...')
for i in range(10000):  # 5000
    sumitup(6000)  # 1000

# add("a", "b")
measure(50000)
