# micro benchmark: method call polymorphic inspired by richards
import time

iteration = 10000


class TaskState(object):
    def __init__(self):
        self.packet_pending = True
        self.task_waiting = False
        self.task_holding = False

    def is_task_holding_or_waiting(self):
        return self.task_holding or (not self.packet_pending and self.task_waiting)


class Task(TaskState):
    def __init__(self):
        TaskState.__init__(self)


class DeviceTask(Task):
    def __init__(self):
        Task.__init__(self)
        self.packet_pending = True


class HandlerTask(Task):
    def __init__(self):
        Task.__init__(self)
        self.task_waiting = True


class IdleTask(Task):
    def __init__(self):
        Task.__init__(self)
        self.task_holding = True


class WorkTask(Task):
    def __init__(self):
        Task.__init__(self)
        self.packet_pending = False


TASK_LIST = [DeviceTask(), DeviceTask(), DeviceTask(), DeviceTask(),
             HandlerTask(), HandlerTask(), HandlerTask(), HandlerTask(),
             IdleTask(), IdleTask(), IdleTask(), IdleTask(),
             WorkTask(), WorkTask(), WorkTask(), WorkTask()]


def do_stuff():
    task_list = TASK_LIST
    total = 0
    for i in range(iteration):
        for t in task_list:
            if (t.is_task_holding_or_waiting()):
                total += 1

    return total


def measure(num):
    print("Start timing...")
    start = time.time()

    for i in range(num):  # 50000
        result = do_stuff()

    print(result)
    duration = "%.3f\n" % (time.time() - start)
    print("call-method-polymorphic: " + duration)


# warm up
print('warming up ...')
for i in range(1000):
    do_stuff()

measure(1000)
