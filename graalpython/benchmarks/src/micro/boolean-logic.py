# micro benchmark: method call polymorphic inspired by richards
import time

iteration = 50000


class Task(object):
    def __init__(self, p, w, h):
        self.packet_pending = p
        self.task_waiting = w
        self.task_holding = h
        self.link = None

    def is_task_holding_or_waiting(self):
        return self.task_holding or (not self.packet_pending and self.task_waiting)


def is_task_holding_or_waiting(task_holding, packet_pending, task_waiting):
    return task_holding or (not packet_pending and task_waiting)


TASK_LIST = [Task(False, False, True),
             Task(False, True, False),
             Task(True, True, False),
             Task(True, False, True)]


def setup_task_queue():
    prev = None
    for t in TASK_LIST:
        t.link = prev
        prev = t
    return t


TASK_QUEUE = setup_task_queue()


def do_stuff():
    total = 0
    for i in range(iteration):
        t = TASK_QUEUE
        while t is not None:
            if (t.is_task_holding_or_waiting()):
                total += 1
            t = t.link

    return total


def no_object_do_stuff():
    p = True
    w = False
    h = True
    total = 0
    for i in range(iteration):
        h = is_task_holding_or_waiting(h, p, w)
        if (is_task_holding_or_waiting(h, p, w)):
            total += 1

    return total


def measure(num):
    print("Start timing...")
    start = time.time()

    for i in range(num):  # 50000
        result = do_stuff()

    print(result)
    duration = "%.3f\n" % (time.time() - start)
    print("boolean-logic: " + duration)


# warm up
print('warming up ...')
for i in range(1000):
    do_stuff()

measure(1000)
