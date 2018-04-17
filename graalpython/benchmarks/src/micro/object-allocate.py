# micro benchmark: attribute access
import time

iteration = 50000  # 50000


class Foo(object):
    def __init__(self, a):
        self.a = a


def do_stuff():
    num = 24
    foo = Foo(0)
    for i in range(iteration):
        num += foo.a % 3
        foo = Foo(num)
    # foo.a = num # replace the line above with this line to remove allocation

    return num


def measure(num):
    print("Start timing...")
    start = time.time()

    for i in range(num):
        result = do_stuff()

    print(result)
    duration = "%.3f\n" % (time.time() - start)
    print("object-allocate: " + duration)


# warm up
print('warming up ...')
for i in range(2000):
    do_stuff()

measure(5000)
