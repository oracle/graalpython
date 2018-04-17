# builtin function len()
import time


def call_len(num, ll):
    length = 0
    for i in range(num):
        ll[i % 5] = i
        length = len(ll)

    return length


def measure():
    print("Start timing...")
    start = time.time()

    ll = [x * 2 for x in range(1000)]
    length = call_len(500000000, ll)  # 1000000000

    print("Final length ", length)

    duration = "%.3f\n" % (time.time() - start)
    print("builtin-len: " + duration)


# warm up
print('warming up ...')
for run in range(5000):
    call_len(10000, [1, 2, 3, 4, 5])

measure()
