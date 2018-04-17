# micro benchmark: list comprehension
import time


def make_list(size):
    return [i for i in range(size)]


def make_lists(num):
    for i in range(num):
        ll = make_list(100000)

    print(ll[-1])


def measure():
    print("Start timing...")
    start = time.time()

    make_lists(5000)  # 50000

    duration = "%.3f\n" % (time.time() - start)
    print("list-comp: " + duration)


# warm up
print('warming up ...')
for i in range(5):
    make_lists(500)

measure()
