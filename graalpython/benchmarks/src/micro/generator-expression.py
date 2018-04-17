# subscribe simple generator
import time


def call_generator(num, iteration):
    item = 0
    for t in range(iteration):
        num += t % 5
        for i in (x * 2 for x in range(num)):
            item = i + item % 5

    return item


def call_generator_localvar(num, iteration):
    item = 0
    for t in range(iteration):
        num += t % 5
        ge = (x * 2 for x in range(num))
        for i in ge:
            item = i + item % 5

    return item


def measure():
    print("Start timing...")
    start = time.time()

    num = 1000
    last_item = call_generator(num, 10000)  # 1000000

    print("Last item ", last_item)

    duration = "%.3f\n" % (time.time() - start)
    print("generator-expression: " + duration)


# warm up
print('warming up ...')
for run in range(2000):
    call_generator(10, 100)

measure()
