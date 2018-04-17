# More work in each generator iteration.
# Various parameters have been tuned to produce 
# a better balanced workload between caller and the generator

import time


def do_each_yield(i, work):
    x = 0
    for j in range(work):
        x = x + (i % 5)

    return x


def generator(num, work):
    for i in range(num):
        yield do_each_yield(i, work)


def call_generator(num, work_consumer, work_producer):
    item = 0
    for i in generator(num, work_producer):
        item += do_each_yield(i, work_consumer)

    return item


def measure(num_of_iterations, work_consumer, work_producer):
    print("Start timing... ", num_of_iterations, work_consumer, work_producer)
    start = time.time()

    last_item = call_generator(num_of_iterations, work_consumer, work_producer)

    print("Last item ", last_item)

    duration = "%.3f\n" % (time.time() - start)
    print("generator-parallel: " + duration)


# warm up
for run in range(100):  # 100
    call_generator(200, 5000, 5000)

# balanced, work decreasing
measure(100000, 20000, 20000)
measure(100000, 10000, 10000)
measure(100000, 5000, 5000)
measure(100000, 2500, 2500)
measure(100000, 1000, 1000)
measure(100000, 500, 500)

# balanced 500, iteration decreasing
measure(10000000, 500, 500)
measure(5000000, 500, 500)
measure(2500000, 500, 500)
measure(1000000, 500, 500)
measure(500000, 500, 500)

# balanced 100, iteration decreasing
measure(10000000, 100, 100)
measure(5000000, 100, 100)
measure(2500000, 100, 100)
measure(1000000, 100, 100)
measure(500000, 100, 100)

# unbalanced, consumer work decreasing
measure(100000, 10000, 10000)
measure(100000, 5000, 10000)
measure(100000, 2500, 10000)
measure(100000, 1000, 10000)
measure(100000, 500, 10000)

# unbalanced, smaller workload, consumer work decreasing
measure(500000, 1000, 1000)
measure(500000, 500, 1000)
measure(500000, 250, 1000)
measure(500000, 100, 1000)
measure(500000, 50, 1000)
