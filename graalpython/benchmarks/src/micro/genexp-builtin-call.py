# generator expression as argument to a built-in call
import time


# test
def _sum(iterable):
    sum = None
    for i in iterable:
        if sum is None:
            sum = i
        else:
            sum += i

    return sum


def call_generator(num, iteration):
    item = 42
    for t in range(iteration):
        num += t % 5
        item = _sum(x % 5 for x in range(num))

    return item


def call_generator_localvar(num, iteration):
    item = 0
    for t in range(iteration):
        num += t % 5
        ge = (x % 5 for x in range(num))
        item = sum(ge)

    return item


def measure():
    num = 1000

    for i in range(5):
        call_generator(num, 10000)  # 1000000

    print("Start timing...")
    start = time.time()

    last_item = call_generator(num, 10000)
    print("Last item ", last_item)

    duration = "%.3f\n" % (time.time() - start)
    print("genexp-builtin-call: " + duration)


# warm up
print('warming up ...')
for run in range(10000):
    call_generator(10, 100)

measure()
