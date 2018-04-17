# function calls
import time


def empty_function(arg):
    return arg


def call_functions(num):
    count = 0
    for i in range(num):
        ret = empty_function(i)
        count += 1

    return count


def measure():
    print("Start timing...")
    start = time.time()

    sum = call_functions(1000000000)  # 1000000

    print("Number of calls ", sum)

    duration = "%.3f\n" % (time.time() - start)
    print("function-call: " + duration)


# warm up
print('warming up ...')
for run in range(10000):
    call_functions(50000)

measure()
