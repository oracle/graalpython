# iterates an int list
import time

LIST = [i % 15 for i in range(900)]


def iterate_list(ll, num):
    for t in range(num):
        iterator = iter(LIST)
        try:
            while True:
                i = next(iterator)
                item = ll[i]
        except StopIteration:
            pass

    return item


def measure():
    print("Start timing...")
    start = time.time()

    last_item = iterate_list(LIST, 1000000)  # 1000000
    print("Last item ", last_item)

    duration = "%.3f\n" % (time.time() - start)
    print("list-iterating-explicit: " + duration)


# warm up
print('warming up ...')
for run in range(100):
    iterate_list(LIST, 1000)

measure()
