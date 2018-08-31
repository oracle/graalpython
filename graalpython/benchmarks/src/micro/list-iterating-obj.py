class Idx:
    __cnt = 0
    def __index__(self):
        cur = self.__cnt
        self.__cnt += 1
        return cur

def iterate_list(ll, num):
    idxObj = Idx()
    for t in range(num):
        item = ll[idxObj]
    return item


def measure(num):
    last_item = iterate_list(list(range(num)), num)
    print("Last item ", last_item)


def __benchmark__(num=1000000):
    measure(num)
