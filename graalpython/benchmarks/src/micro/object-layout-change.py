# object layout change
import time


class Foo(object):
    def __init__(self, a):
        self.a = a

    def mod(self, b):
        self.b = b % 5


def do_change(n):
    res = 0
    for i in range(n):
        f = Foo(i)
        f.mod(res)
        res += f.a + f.b
    return res


def main(n):
    for i in range(n):
        res = do_change(100)


def measure(n):
    print("Start timing...")
    start = time.time()
    main(n)
    duration = "%.3f\n" % (time.time() - start)
    print("object-layout-change: " + duration)


print('warming up ...')
for i in range(100):
    main(500)

measure(1000000)
