# micro benchmark: attribute access
import time

iteration = 50000  # 50000


class Foo(object):
    def __init__(self, a):
        self.a = a


def do_stuff(foo):
    for i in range(iteration):
        local_a = foo.a + 1
        foo.a = local_a % 5

    return foo.a


def measure(num):
    print("Start timing...")
    start = time.time()

    for i in range(num):  # 50000
        result = do_stuff(Foo(42))

    print(result)
    duration = "%.3f\n" % (time.time() - start)
    print("attribute-access: " + duration)


# warm up
print('warming up ...')
for i in range(2000):
    do_stuff(Foo(42))

measure(5000)
