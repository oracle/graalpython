# subscribe list by index
import time


def index_list(ll, num):
    ll_len = len(ll)
    item = 0

    for t in range(num):
        for i in range(ll_len):
            item = (item + ll[i]) % 7

    return item


def measure():
    print("Start timing...")
    start = time.time()

    ll = [x * 2 for x in range(1000)]
    last_item = index_list(ll, 1000000)  # 1000000

    print("Last item ", last_item)

    duration = "%.3f\n" % (time.time() - start)
    print("list-indexing: " + duration)


# warm up
print('warming up ...')
for run in range(1200):
    index_list([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 10000)

measure()
