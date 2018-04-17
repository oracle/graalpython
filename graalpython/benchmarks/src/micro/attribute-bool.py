# micro benchmark: boolean attribute access
import time

iteration = 500000


class TaskState(object):
    def __init__(self):
        self.packet_pending = True
        self.task_waiting = False
        self.task_holding = False

    def packet_pending(self):
        self.packet_pending = True
        self.task_waiting = False
        self.task_holding = False
        return self

    def waiting(self):
        self.packet_pending = False
        self.task_waiting = True
        self.task_holding = False
        return self

    def running(self):
        self.packet_pending = False
        self.task_waiting = False
        self.task_holding = False
        return self


def do_stuff():
    task = TaskState()

    for i in range(iteration):
        task.waiting()
        task.running()

        if task.task_waiting:
            task.task_holding = False

    return task.task_holding


def measure(num):
    print("Start timing...")
    start = time.time()

    for i in range(num):
        result = do_stuff()

    print(result)
    duration = "%.3f\n" % (time.time() - start)
    print("attribute-bool: " + duration)


# warm up
print('warming up ...')
for i in range(2000):
    do_stuff()

measure(3000)
