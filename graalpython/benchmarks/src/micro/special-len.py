# special method __len__ dispatch
import time


class Num(object):
    def __init__(self, n):
        self.n = n

    def __len__(self):
        return self.n

    def __repr__(self):
        return repr(self.n)


def do_compute(num):
    for i in range(num):
        sum_ = 0
        one = Num(42)
        j = 0
        while j < num:
            sum_ = sum_ + len(one)
            j += 1

    return sum_


def measure(num):
    for run in range(3):
        do_compute(20000)  # 10000

    print("Start timing...")
    start = time.time()

    for run in range(num):
        sum_ = do_compute(20000)  # 10000

    print("sum", sum_)

    duration = "%.3f\n" % (time.time() - start)
    print("special-len: " + duration)


print('warming up ...')
for run in range(2000):
    do_compute(5)  # 1000

measure(5)
