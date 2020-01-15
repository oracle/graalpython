class sub_int(int):
    pass


def getitem(d, num):
    for t in range(num):
        item = d.get(num)
        item2 = d.get(sub_int(num))

    return item, item2


def measure(num):
    d = {x: x**x for x in range(1000)}
    last_items = getitem(d, num)  # 1000000
    print("Last items ", last_items)


def __benchmark__(num=1000000):
    measure(num)
