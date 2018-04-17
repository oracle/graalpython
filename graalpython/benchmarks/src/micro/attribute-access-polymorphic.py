# micro benchmark: attribute access polymorphic inspired by richards
import time

iteration = 20000  # 50000


class TaskState(object):
    pass


class Task(TaskState):
    def __init__(self, foo):
        self.foo = foo


class DeviceTask(Task):
    def __init__(self, foo):
        Task.__init__(self, foo)


class HandlerTask(Task):
    def __init__(self, foo):
        Task.__init__(self, foo)


class IdleTask(Task):
    def __init__(self, foo):
        Task.__init__(self, foo)


class WorkTask(Task):
    def __init__(self, foo):
        Task.__init__(self, foo)


TASK_LIST = [DeviceTask(0), DeviceTask(1), DeviceTask(2), DeviceTask(3),
             HandlerTask(4), HandlerTask(5), HandlerTask(6), HandlerTask(7),
             IdleTask(8), IdleTask(9), IdleTask(10), IdleTask(11),
             WorkTask(12), WorkTask(13), WorkTask(14), WorkTask(15)]


def do_stuff():
    task_list = TASK_LIST
    total = 0
    for i in range(iteration):
        for t in task_list:
            total = (total + t.foo) % 7

    return total


def measure(num):
    for i in range(3):
        do_stuff()

    print("Start timing...")
    start = time.time()

    for i in range(num):  # 50000
        result = do_stuff()

    print(result)
    duration = "%.3f\n" % (time.time() - start)
    print("attribute-access-polymorphic: " + duration)


# warm up
print('warming up ...')
for i in range(1000):
    do_stuff()

measure(1000)
