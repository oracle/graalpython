# Copyright (c) 2017, 2018, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# micro benchmark: method call polymorphic inspired by richards

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
    for i in range(num):  # 50000
        result = do_stuff()

    print(result)
    

def __benchmark__(num=1000):
    measure(num)
